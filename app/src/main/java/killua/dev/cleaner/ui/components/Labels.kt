package killua.dev.cleaner.ui.components

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import killua.dev.cleaner.R
import killua.dev.cleaner.data.settings.ThemeMode
import killua.dev.cleaner.domain.model.FileEventType
import killua.dev.cleaner.domain.model.StorageAccess
import killua.dev.cleaner.domain.model.StorageBackend
import killua.dev.cleaner.domain.model.StorageLocation

@StringRes
fun StorageLocation.labelRes(): Int = when (this) {
    StorageLocation.DataFolder -> R.string.location_data
    StorageLocation.CacheFolder -> R.string.location_cache
}

@StringRes
fun StorageLocation.descriptionRes(): Int = when (this) {
    StorageLocation.DataFolder -> R.string.location_data_description
    StorageLocation.CacheFolder -> R.string.location_cache_description
}

@StringRes
fun StorageAccess.labelRes(): Int = when (this) {
    StorageAccess.None -> R.string.access_none
    StorageAccess.Media -> R.string.access_media
    StorageAccess.ReadOnly -> R.string.access_read_only
    StorageAccess.ReadWrite -> R.string.access_read_write
    StorageAccess.AllFiles -> R.string.access_all_files
}

@StringRes
fun StorageAccess.recommendationRes(): Int = when (this) {
    StorageAccess.None -> R.string.recommend_none
    StorageAccess.Media -> R.string.recommend_media
    StorageAccess.ReadOnly -> R.string.recommend_read_only
    StorageAccess.ReadWrite -> R.string.recommend_read_write
    StorageAccess.AllFiles -> R.string.recommend_all_files
}

@StringRes
fun StorageBackend.labelRes(): Int = when (this) {
    StorageBackend.Fuse -> R.string.backend_fuse
    StorageBackend.Sdcardfs -> R.string.backend_sdcardfs
    StorageBackend.FuseBpf -> R.string.backend_fuse_bpf
    StorageBackend.Unknown -> R.string.backend_unknown
}

@StringRes
fun FileEventType.labelRes(): Int = when (this) {
    FileEventType.Create -> R.string.event_create
    FileEventType.Modify -> R.string.event_modify
    FileEventType.Delete -> R.string.event_delete
    FileEventType.MovedFrom -> R.string.event_moved_from
    FileEventType.MovedTo -> R.string.event_moved_to
    FileEventType.CreateDir -> R.string.event_create_dir
    FileEventType.DeleteDir -> R.string.event_delete_dir
    FileEventType.Unknown -> R.string.event_unknown
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@Composable
fun locationLabel(location: StorageLocation?, default: StorageLocation): String =
    if (location == null) {
        stringResource(R.string.location_follow_default, stringResource(default.labelRes()))
    } else {
        stringResource(location.labelRes())
    }

fun Context.formatSize(bytes: Long): String = Formatter.formatShortFileSize(this, bytes)

fun Context.formatDateTime(millis: Long): String = DateUtils.formatDateTime(
    this,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
)

@Composable
fun rememberFormattedSize(bytes: Long): String = LocalContext.current.formatSize(bytes)

@Composable
fun rememberFormattedTime(millis: Long): String = LocalContext.current.formatDateTime(millis)

@Composable
fun rememberRelativeTime(millis: Long): String = DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE,
).toString()
