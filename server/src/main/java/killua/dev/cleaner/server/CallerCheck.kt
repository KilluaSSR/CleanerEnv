package killua.dev.cleaner.server

import android.system.Os
import killua.dev.cleaner.ipc.IpcConstants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class CallerCheck {
    private val appUids = ConcurrentHashMap<Int, Int>()

    fun isAllowed(uid: Int): Boolean {
        if (uid == ROOT_UID || uid == SHELL_UID) return true
        val userId = uid / PER_USER_RANGE
        return appUid(userId) == uid
    }

    fun installedUsers(): List<Int> =
        File("/data/user").listFiles()
            ?.mapNotNull { it.name.toIntOrNull() }
            ?.filter { appUid(it) != null }
            .orEmpty()

    private fun appUid(userId: Int): Int? {
        appUids[userId]?.let { return it }
        val managerDataPath = "/data/user/$userId/${IpcConstants.APPLICATION_ID}"
        val uid = runCatching { Os.stat(managerDataPath).st_uid }.getOrNull() ?: return null
        appUids[userId] = uid
        return uid
    }

    private companion object {
        const val ROOT_UID = 0
        const val SHELL_UID = 2000
        const val PER_USER_RANGE = 100_000
    }
}
