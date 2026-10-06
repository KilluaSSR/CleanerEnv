package killua.dev.cleaner.guard;

import android.net.Uri;

import java.util.List;

final class MediaUris {
    private MediaUris() {
    }

    static boolean isGuardedCollection(Uri uri) {
        List<String> segments = uri == null ? null : uri.getPathSegments();
        if (segments == null || segments.size() < 2 || !isPrimaryVolume(segments.get(0))) {
            return false;
        }
        switch (segments.get(1)) {
            case "images":
            case "video":
            case "audio":
                return segments.size() >= 3 && segments.get(2).equals("media");
            case "file":
            case "downloads":
                return true;
            default:
                return false;
        }
    }
    static boolean isGuardedItem(Uri uri) {
        if (!isGuardedCollection(uri)) {
            return false;
        }
        List<String> segments = uri.getPathSegments();
        int idIndex = segments.get(1).equals("file") || segments.get(1).equals("downloads") ? 2 : 3;
        return segments.size() == idIndex + 1 && isNumber(segments.get(idIndex));
    }
    /** Id of a guarded item URI, {@code -1} for anything else. */
    static long itemId(Uri uri) {
        if (!isGuardedItem(uri)) {
            return -1;
        }
        try {
            return Long.parseLong(uri.getLastPathSegment());
        } catch (NumberFormatException tooLong) {
            return -1;
        }
    }

    static String defaultFolder(Uri uri) {
        switch (uri.getPathSegments().get(1)) {
            case "images":
                return "Pictures";
            case "video":
                return "Movies";
            case "audio":
                return "Music";
            default:
                return "Download";
        }
    }

    private static boolean isPrimaryVolume(String volume) {
        return volume.equals("external") || volume.equals("external_primary");
    }

    private static boolean isNumber(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
