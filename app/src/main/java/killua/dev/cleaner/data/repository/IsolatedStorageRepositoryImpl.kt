package killua.dev.cleaner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import android.system.OsConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import killua.dev.arsenal.permission.hasAllFilesAccess
import killua.dev.cleaner.data.backend.ServerConnection
import killua.dev.cleaner.data.toDomain
import killua.dev.cleaner.domain.IsolatedStorageRepository
import killua.dev.cleaner.domain.model.FileEntry
import killua.dev.cleaner.ipc.RuleFileCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsolatedStorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val daemonLink: ServerConnection,
) : IsolatedStorageRepository {

    override suspend fun list(packageName: String, userId: Int, subPath: String): List<FileEntry> =
        daemonLink.call { it.listIsolated(packageName, userId, subPath) }.orEmpty().map { it.toDomain() }

    override suspend fun analyze(packageName: String, userId: Int): List<FileEntry> =
        daemonLink.call { it.analyzeIsolated(packageName, userId) }.orEmpty().map { it.toDomain() }

    override suspend fun media(packageName: String, userId: Int, limit: Int): List<FileEntry> =
        daemonLink.call { it.listIsolatedMedia(packageName, userId, limit) }.orEmpty().map { it.toDomain() }

    override suspend fun realFolders(userId: Int, subPath: String): List<String>? =
        localFolders(userId, subPath) ?: daemonLink.call { it.listRealFolders(userId, subPath) }

    override suspend fun isolatedFolders(packageName: String, userId: Int, subPath: String): List<String>? =
        daemonLink.call { it.listIsolated(packageName, userId, subPath) }
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)

    private suspend fun localFolders(userId: Int, subPath: String): List<String>? = withContext(Dispatchers.IO) {
        if (!context.hasAllFilesAccess()) return@withContext null
        val relative = if (subPath.isEmpty()) "" else RuleFileCodec.normalizeRelativePath(subPath) ?: return@withContext null
        if (APP_PRIVATE_DIRS.any { relative == it || relative.startsWith("$it/") }) return@withContext null
        File("$SHARED_STORAGE/$userId", relative)
            .listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.map { it.name }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    override suspend fun thumbnail(packageName: String, userId: Int, subPath: String, maxSizePx: Int): Bitmap? {
        val pfd = daemonLink.call { it.openIsolated(packageName, userId, subPath) } ?: return null
        return withContext(Dispatchers.IO) {
            pfd.use { descriptor ->
                runCatching {
                    val fd = descriptor.fileDescriptor
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fd, null, bounds)
                    Os.lseek(fd, 0, OsConstants.SEEK_SET)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
                    }
                    BitmapFactory.decodeFileDescriptor(fd, null, options)
                }.getOrNull()
            }
        }
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    private companion object {
        const val SHARED_STORAGE = "/storage/emulated"
        val APP_PRIVATE_DIRS = listOf("Android/data", "Android/obb")
    }
}
