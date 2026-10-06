package killua.dev.cleaner.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(256)
private val appLabelMemo = LruCache<String, String>(512)

@Composable
fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    val label by produceState(initialValue = appLabelMemo.get(packageName) ?: packageName, packageName) {
        if (appLabelMemo.get(packageName) != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString().also { appLabelMemo.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }
    return label
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = "$packageName@$sizePx"
    val icon by produceState(initialValue = iconCache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
                    .also { iconCache.put(key, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Spacer(modifier.size(size))
    }
}
