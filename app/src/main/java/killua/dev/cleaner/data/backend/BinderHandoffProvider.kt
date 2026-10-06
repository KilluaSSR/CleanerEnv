package killua.dev.cleaner.data.backend

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import killua.dev.cleaner.ipc.IpcConstants

class BinderHandoffProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun serverConnection(): ServerConnection
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != IpcConstants.METHOD_SEND_BINDER || Binder.getCallingUid() != Process.ROOT_UID) return null
        val binder = extras?.getBinder(IpcConstants.EXTRA_BINDER) ?: return null
        val context = context?.applicationContext ?: return null
        EntryPointAccessors.fromApplication(context, ProviderEntryPoint::class.java)
            .serverConnection()
            .acceptDaemonBinder(binder)
        return Bundle()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
