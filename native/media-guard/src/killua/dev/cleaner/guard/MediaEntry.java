package killua.dev.cleaner.guard;

import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** A file in an app's isolated storage, presented as a MediaStore row to that app. */
final class MediaEntry {
    static final int TYPE_NONE = 0;
    static final int TYPE_IMAGE = 1;
    static final int TYPE_AUDIO = 2;
    static final int TYPE_VIDEO = 3;

    /** Columns of the in-memory table that app selections and sort orders run against. */
    static final String[] COLUMNS = {
            "_id", "_data", "_display_name", "_size", "mime_type", "media_type", "title",
            "date_added", "date_modified", "date_expires", "datetaken", "width", "height",
            "orientation", "duration", "resolution", "relative_path", "bucket_id",
            "bucket_display_name", "volume_name", "owner_package_name", "is_pending", "is_trashed",
            "is_favorite", "is_download", "is_drm", "is_music", "is_podcast", "is_ringtone",
            "is_alarm", "is_notification", "is_audiobook", "is_recording", "artist", "album",
            "album_artist", "composer", "genre", "year", "track", "album_id", "artist_id",
            "genre_id", "parent", "format", "document_id", "instance_id", "original_document_id",
            "_hash", "generation_added", "generation_modified", "special_format", "description",
            "author", "writer", "bitrate", "capture_framerate", "cd_track_number", "disc_number",
            "num_tracks", "compilation", "xmp", "is_private", "isprivate", "bookmark", "tags",
            "category", "language", "latitude", "longitude", "mini_thumb_magic", "picasa_id",
            "_transcode_status", "_video_codec_type", "_modifier",
    };
    static final Set<String> TEXT_COLUMNS = new HashSet<>(Arrays.asList(
            "_data", "_display_name", "mime_type", "title", "resolution", "relative_path",
            "bucket_id", "bucket_display_name", "volume_name", "owner_package_name", "artist",
            "album", "album_artist", "composer", "genre", "document_id", "instance_id",
            "original_document_id", "_hash", "description", "author", "writer", "cd_track_number",
            "disc_number", "compilation", "tags", "category", "language", "picasa_id"));
    /** Columns whose values need the file to be decoded. */
    static final Set<String> DETAIL_COLUMNS = new HashSet<>(Arrays.asList(
            "width", "height", "orientation", "duration", "resolution", "artist", "album",
            "album_artist", "composer", "genre", "year", "title"));

    final long id;
    final int ownerUid;
    final String ownerPackage;
    final int user;
    final File file;
    final String viewPath;
    final String shownPath;
    final String name;
    final String mime;
    final int mediaType;
    final long size;
    final long modified;

    private volatile boolean detailed;
    private long width;
    private long height;
    private long orientation;
    private long duration;
    private String title;
    private String artist;
    private String album;

    MediaEntry(long id, int ownerUid, String ownerPackage, int user, File file, String viewPath,
               String shownPath, long size, long modified) {
        this.id = id;
        this.ownerUid = ownerUid;
        this.ownerPackage = ownerPackage;
        this.user = user;
        this.file = file;
        this.viewPath = viewPath;
        this.shownPath = shownPath;
        int slash = viewPath.lastIndexOf('/');
        this.name = viewPath.substring(slash + 1);
        this.mime = mimeOf(name);
        this.mediaType = typeOf(mime);
        this.size = size;
        this.modified = modified;
    }

    /** Same file at a new id-preserving path, or with new stat values. */
    MediaEntry with(File file, String viewPath, String shownPath, long size, long modified) {
        MediaEntry next = new MediaEntry(id, ownerUid, ownerPackage, user, file, viewPath, shownPath,
                size, modified);
        if (detailed && size == this.size && modified == this.modified && next.mime.equals(mime)) {
            next.copyDetails(this);
        }
        return next;
    }

    boolean sameStat(long size, long modified) {
        return this.size == size && this.modified == modified;
    }

    boolean isDetailed() {
        return detailed || (mediaType != TYPE_IMAGE && mediaType != TYPE_VIDEO && mediaType != TYPE_AUDIO);
    }

    static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type == null ? "application/octet-stream" : type;
    }

    static int typeOf(String mime) {
        if (mime.startsWith("image/")) {
            return TYPE_IMAGE;
        }
        if (mime.startsWith("video/")) {
            return TYPE_VIDEO;
        }
        if (mime.startsWith("audio/") || mime.equals("application/ogg")) {
            return TYPE_AUDIO;
        }
        return TYPE_NONE;
    }

    String data() {
        return "/storage/emulated/" + user + "/" + viewPath;
    }

    String relativeDir() {
        int slash = shownPath.lastIndexOf('/');
        return slash < 0 ? "/" : shownPath.substring(0, slash + 1);
    }

    String viewDir() {
        int slash = viewPath.lastIndexOf('/');
        return slash < 0 ? "" : viewPath.substring(0, slash);
    }

    Object value(String column) {
        boolean visual = mediaType == TYPE_IMAGE || mediaType == TYPE_VIDEO;
        boolean timed = mediaType == TYPE_VIDEO || mediaType == TYPE_AUDIO;
        switch (column) {
            case "_id":
                return id;
            case "_data":
                return data();
            case "_display_name":
                return name;
            case "_size":
                return size;
            case "mime_type":
                return mime;
            case "media_type":
                return (long) mediaType;
            case "title": {
                if (title != null) {
                    return title;
                }
                int dot = name.lastIndexOf('.');
                return dot > 0 ? name.substring(0, dot) : name;
            }
            case "date_added":
            case "date_modified":
                return modified / 1000;
            case "datetaken":
                return visual ? modified : null;
            case "width":
                return visual ? width : null;
            case "height":
                return visual ? height : null;
            case "orientation":
                return visual ? orientation : null;
            case "duration":
                return timed ? duration : null;
            case "resolution":
                return visual && width > 0 && height > 0 ? width + "\u00d7" + height : null;
            case "relative_path":
                return relativeDir();
            case "bucket_id": {
                File parent = new File("/storage/emulated/" + user + "/" + shownPath).getParentFile();
                return parent == null ? null : String.valueOf(parent.getPath().toLowerCase(Locale.ROOT).hashCode());
            }
            case "bucket_display_name": {
                File parent = new File("/storage/emulated/" + user + "/" + shownPath).getParentFile();
                return parent == null ? null : parent.getName();
            }
            case "volume_name":
                return "external_primary";
            case "owner_package_name":
                return ownerPackage;
            case "is_pending":
            case "is_trashed":
            case "is_favorite":
            case "is_drm":
            case "is_podcast":
            case "is_ringtone":
            case "is_alarm":
            case "is_notification":
            case "is_audiobook":
            case "is_recording":
            case "special_format":
            case "generation_added":
            case "generation_modified":
                return 0L;
            case "is_download":
                return PathNames.within(PathNames.normalize(relativeDir()), "Download") ? 1L : 0L;
            case "is_music":
                return mediaType == TYPE_AUDIO ? 1L : 0L;
            case "artist":
                return artist;
            case "album":
                return album;
            default:
                return null;
        }
    }

    /** Decodes dimensions, orientation and duration; cheap headers only for images. */
    void loadDetails() {
        if (detailed) {
            return;
        }
        String path = file.getPath();
        try {
            if (mediaType == TYPE_IMAGE) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                width = Math.max(bounds.outWidth, 0);
                height = Math.max(bounds.outHeight, 0);
                try {
                    int exif = new ExifInterface(path).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    orientation = exif == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                            : exif == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                            : exif == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                } catch (Exception noExif) {
                    orientation = 0;
                }
            } else if (mediaType == TYPE_VIDEO || mediaType == TYPE_AUDIO) {
                try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                    retriever.setDataSource(path);
                    duration = number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    if (mediaType == TYPE_VIDEO) {
                        width = number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                        height = number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                        orientation = number(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                    } else {
                        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    }
                }
            }
        } catch (Exception undecodable) {
            // Keeps the zero defaults: the row stays listed, just without dimensions.
        }
        detailed = true;
    }

    private void copyDetails(MediaEntry other) {
        width = other.width;
        height = other.height;
        orientation = other.orientation;
        duration = other.duration;
        title = other.title;
        artist = other.artist;
        album = other.album;
        detailed = true;
    }

    private static long number(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException notNumber) {
            return 0;
        }
    }
}
