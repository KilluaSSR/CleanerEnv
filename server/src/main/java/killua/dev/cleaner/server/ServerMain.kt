package killua.dev.cleaner.server

import android.os.Build
import android.os.Looper
import android.os.Process
import kotlin.system.exitProcess

object ServerMain {

    @JvmStatic
    fun main(args: Array<String>) {
        ServerLog.i("starting (uid=${Process.myUid()}, sdk=${Build.VERSION.SDK_INT})")
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            ServerLog.e("fatal", error)
            exitProcess(1)
        }
        Looper.prepareMainLooper()
        IsolationDaemon().start()
        Looper.loop()
        error("main looper exited")
    }
}
