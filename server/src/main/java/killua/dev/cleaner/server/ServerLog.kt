package killua.dev.cleaner.server

import android.util.Log

internal object ServerLog {
    private const val TAG = "IsolationDaemon"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
    }
}
