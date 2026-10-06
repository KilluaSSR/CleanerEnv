package killua.dev.cleaner.ipc

object RuleFileCodec {
    const val FORMAT_VERSION = 1

    fun encode(rule: RuleParcel): String = buildString {
        appendLine("v=$FORMAT_VERSION")
        appendLine("enabled=${rule.enabled.flag()}")
        appendLine("location=${rule.location}")
        appendLine("gallery=${rule.allowGallery.flag()}")
        appendLine("providers=${rule.handleProviders.flag()}")
        rule.accessibleFolders.mapNotNull(::normalizeRelativePath).distinct().forEach {
            appendLine("accessible=$it")
        }
        rule.exportSources.zip(rule.exportTargets).forEach { (source, target) ->
            val view = normalizeRelativePath(source) ?: return@forEach
            val real = normalizeRelativePath(target) ?: return@forEach
            appendLine("export=$view\t$real")
        }
        rule.sharedPackages.zip(rule.sharedPaths).forEach { (pkg, path) ->
            val relative = normalizeRelativePath(path) ?: return@forEach
            if (isValidPackageName(pkg)) appendLine("shared=$pkg\t$relative")
        }
    }

    fun decode(packageName: String, userId: Int, text: String): RuleParcel {
        val values = text.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toList()
        fun flag(key: String) = values.any { it.first == key && it.second == "1" }
        fun all(key: String) = values.filter { it.first == key }.map { it.second }
        fun pairs(key: String) = all(key).mapNotNull { value ->
            val tab = value.indexOf('\t')
            if (tab <= 0) null else value.substring(0, tab) to value.substring(tab + 1)
        }
        val exports = pairs("export")
        val shared = pairs("shared")
        return RuleParcel(
            packageName = packageName,
            userId = userId,
            enabled = flag("enabled"),
            location = all("location").lastOrNull()?.toIntOrNull() ?: IpcConstants.LOCATION_DATA,
            accessibleFolders = all("accessible"),
            allowGallery = flag("gallery"),
            handleProviders = flag("providers"),
            exportSources = exports.map { it.first },
            exportTargets = exports.map { it.second },
            sharedPackages = shared.map { it.first },
            sharedPaths = shared.map { it.second },
        )
    }

    fun normalizeRelativePath(path: String): String? {
        if (path.any { it == '\t' || it == '\n' || it == '\r' || it == '\u0000' }) return null
        val segments = path.trim().split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    fun isValidPackageName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 255 &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' }

    private fun Boolean.flag(): Int = if (this) 1 else 0
}
