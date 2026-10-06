package killua.dev.cleaner.server

import android.os.ParcelFileDescriptor
import killua.dev.cleaner.ipc.FileEntryParcel
import killua.dev.cleaner.ipc.IpcConstants
import killua.dev.cleaner.ipc.RuleFileCodec
import java.io.File

internal class IsolatedStorage(private val rules: RuleStore) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

    fun list(packageName: String, userId: Int, subPath: String): List<FileEntryParcel> {
        val root = root(packageName, userId) ?: return emptyList()
        val dir = resolve(packageName, userId, subPath) ?: return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children.map { it.toEntry(root) }.sortedWith(
            compareByDescending<FileEntryParcel> { it.isDirectory }.thenBy { it.name.lowercase() },
        )
    }


    fun realFolders(userId: Int, subPath: String): List<String> {
        val root = File("/data/media/$userId")
        val dir = if (subPath.isEmpty()) {
            root
        } else {
            val normalized = RuleFileCodec.normalizeRelativePath(subPath) ?: return emptyList()
            File(root, normalized).takeIf { it.canonicalPath.startsWith("${root.canonicalPath}/") } ?: return emptyList()
        }
        return dir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.map { it.name }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            .orEmpty()
    }

    fun analyze(packageName: String, userId: Int): List<FileEntryParcel> {
        val root = root(packageName, userId) ?: return emptyList()
        val children = root.listFiles() ?: return emptyList()
        return children.map { child ->
            val size = if (child.isDirectory) sizeOf(child) else child.length()
            child.toEntry(root).copy(size = size)
        }.sortedByDescending { it.size }
    }

    fun media(packageName: String, userId: Int, limit: Int): List<FileEntryParcel> {
        val root = root(packageName, userId) ?: return emptyList()
        val found = ArrayList<FileEntryParcel>()
        walk(root, root, found, limit.coerceIn(1, 2000))
        return found.sortedByDescending { it.lastModified }
    }

    fun open(packageName: String, userId: Int, subPath: String): ParcelFileDescriptor? {
        val file = resolve(packageName, userId, subPath) ?: return null
        if (!file.isFile) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }

    private fun walk(root: File, dir: File, out: MutableList<FileEntryParcel>, limit: Int) {
        if (out.size >= limit) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (out.size >= limit) return
            if (child.isDirectory) {
                walk(root, child, out, limit)
            } else if (child.extension.lowercase() in imageExtensions) {
                out.add(child.toEntry(root))
            }
        }
    }

    private fun sizeOf(file: File): Long {
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
    }

    private fun root(packageName: String, userId: Int): File? {
        if (!RuleFileCodec.isValidPackageName(packageName)) return null
        val location = rules.get(packageName, userId)?.location ?: IpcConstants.LOCATION_DATA
        val relative = IpcConstants.isolatedRelativeRoot(packageName, location)
        return File("/data/media/$userId/$relative")
    }

    private fun resolve(packageName: String, userId: Int, subPath: String): File? {
        val root = root(packageName, userId) ?: return null
        if (subPath.isEmpty()) return root
        val normalized = RuleFileCodec.normalizeRelativePath(subPath) ?: return null
        val resolvedFile = File(root, normalized)
        val rootPath = root.canonicalPath
        val resolvedPath = resolvedFile.canonicalPath
        return if (resolvedPath == rootPath || resolvedPath.startsWith("$rootPath/")) resolvedFile else null
    }

    private fun File.toEntry(base: File): FileEntryParcel {
        val relative = relativeToOrSelf(base).path
        return FileEntryParcel(
            name = name,
            subPath = if (relative == ".") "" else relative,
            isDirectory = isDirectory,
            size = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            childCount = if (isDirectory) (list()?.size ?: 0) else 0,
        )
    }
}
