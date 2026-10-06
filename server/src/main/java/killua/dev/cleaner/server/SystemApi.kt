package killua.dev.cleaner.server

import android.content.AttributionSource
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import killua.dev.cleaner.ipc.IpcConstants
import java.lang.reflect.Method

object SystemApi {

    fun property(key: String, default: String = ""): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, key, default) as String
    }.getOrDefault(default)

    fun sendBinderToApp(applicationId: String, userId: Int, binder: IBinder): Boolean {
        val authority = applicationId + IpcConstants.BINDER_PROVIDER_SUFFIX
        val am = amProxy() ?: return false
        val token: IBinder? = null
        return try {
            val provider = getContentProviderExternal(am, authority, userId, token) ?: return false
            val extras = Bundle(1).apply { putBinder(IpcConstants.EXTRA_BINDER, binder) }
            callProvider(provider, authority, IpcConstants.METHOD_SEND_BINDER, extras)
            true
        } catch (error: Throwable) {
            ServerLog.w("sendBinder to user $userId failed", error)
            false
        } finally {
            runCatching { removeContentProviderExternal(am, authority, token, userId) }
        }
    }

    private fun amProxy(): Any? = runCatching {
        Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
    }.getOrNull()

    private fun getContentProviderExternal(am: Any, name: String, userId: Int, token: IBinder?): Any? {
        val method = am.javaClass.methods.first { it.name == "getContentProviderExternal" }
        val providerHolder = when (method.parameterTypes.size) {
            // R+: (String name, int userId, IBinder token, String tag)
            4 -> method.invoke(am, name, userId, token, name)
            // Q: (String name, int userId, IBinder token)
            else -> method.invoke(am, name, userId, token)
        } ?: return null
        return providerHolder.javaClass.getField("provider").get(providerHolder)
    }

    private fun removeContentProviderExternal(am: Any, name: String, token: IBinder?, userId: Int) {
        val asUser = am.javaClass.methods.firstOrNull { it.name == "removeContentProviderExternalAsUser" }
        if (asUser != null) {
            asUser.invoke(am, name, token, userId)
        } else {
            am.javaClass.getMethod("removeContentProviderExternal", String::class.java, IBinder::class.java)
                .invoke(am, name, token)
        }
    }

    private fun callProvider(provider: Any, authority: String, method: String, extras: Bundle): Bundle? {
        val calls = provider.javaClass.methods.filter { it.name == "call" }
        fun find(predicate: (Method) -> Boolean) = calls.firstOrNull(predicate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // S+: (AttributionSource, String authority, String method, String arg, Bundle extras)
            val call = find { it.parameterTypes.firstOrNull() == AttributionSource::class.java }
            if (call != null) {
                val source = AttributionSource.Builder(Process.myUid()).build()
                return call.invoke(provider, source, authority, method, null, extras) as Bundle?
            }
        }
        // R: (String pkg, String attributionTag, String authority, String method, String arg, Bundle)
        find { it.parameterTypes.size == 6 }?.let {
            return it.invoke(provider, null, null, authority, method, null, extras) as Bundle?
        }
        // Q: (String pkg, String authority, String method, String arg, Bundle extras)
        val call = find { it.parameterTypes.size == 5 } ?: error("no compatible IContentProvider.call")
        return call.invoke(provider, null, authority, method, null, extras) as Bundle?
    }
}
