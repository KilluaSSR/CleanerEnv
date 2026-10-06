package killua.dev.cleaner.ipc

data class WatchSpec(
    val id: String,
    val directory: String,
    val kind: Int,
    val name: String,
    val recursive: Boolean,
    val enabled: Boolean,
) {
    private val namePattern: Regex by lazy { globRegex(name) }

    fun matchesName(candidate: String): Boolean = namePattern.matches(candidate)

    private companion object {
        fun globRegex(glob: String): Regex = buildString {
            glob.forEach { char ->
                when (char) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(char.toString()))
                }
            }
        }.toRegex(RegexOption.IGNORE_CASE)
    }
}

object WatchCodec {
    private val ID = Regex("[A-Za-z0-9_-]{1,64}")
    private const val FIELDS = 6

    fun isValidId(id: String): Boolean = ID.matches(id)

    fun normalizeDirectory(path: String): String? {
        if (path.any { it == '\t' || it == '\n' || it == '\r' || it == '\u0000' }) return null
        val segments = path.trim().split('/').filter { it.isNotEmpty() }
        if (segments.any { it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name.none { it == '/' || it == '\t' || it == '\n' || it == '\r' || it == '\u0000' }

    fun encode(specs: List<WatchSpec>): String = buildString {
        specs.forEach { spec ->
            require(isValidId(spec.id)) { "invalid watch id ${spec.id}" }
            val directory = requireNotNull(normalizeDirectory(spec.directory)) { "invalid directory ${spec.directory}" }
            val name = if (spec.kind == IpcConstants.WATCH_KIND_ALL) "" else spec.name.trim()
            require(spec.kind == IpcConstants.WATCH_KIND_ALL || isValidName(name)) { "invalid name ${spec.name}" }
            append(spec.id).append('\t')
            append(spec.kind).append('\t')
            append(if (spec.recursive) '1' else '0').append('\t')
            append(if (spec.enabled) '1' else '0').append('\t')
            append(directory).append('\t')
            append(name).append('\n')
        }
    }

    fun decode(text: String): List<WatchSpec> = text.lineSequence().mapNotNull { line ->
        val fields = line.trimEnd('\r').split('\t')
        if (fields.size != FIELDS || !isValidId(fields[0])) return@mapNotNull null
        val kind = fields[1].toIntOrNull()?.takeIf { it in IpcConstants.WATCH_KIND_FOLDER..IpcConstants.WATCH_KIND_ALL }
            ?: return@mapNotNull null
        val directory = normalizeDirectory(fields[4]) ?: return@mapNotNull null
        if (kind != IpcConstants.WATCH_KIND_ALL && !isValidName(fields[5])) return@mapNotNull null
        WatchSpec(
            id = fields[0],
            directory = directory,
            kind = kind,
            name = fields[5],
            recursive = fields[2] == "1",
            enabled = fields[3] == "1",
        )
    }.toList()
}
