package killua.dev.cleaner.guard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PathNames {
    private static final Pattern PRIMARY_ROOT =
            Pattern.compile("^(/storage/emulated/\\d+/|/storage/self/primary/|/sdcard/)(.*)$");

    private PathNames() {
    }
    static String normalize(String path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path.trim().split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals(".") || segment.equals("..")) {
                return null;
            }
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(segment);
        }
        return out.toString();
    }

    static boolean within(String dir, String base) {
        int length = base.length();
        if (dir.length() == length) {
            return dir.equalsIgnoreCase(base);
        }
        return dir.length() > length
                && dir.charAt(length) == '/'
                && dir.regionMatches(true, 0, base, 0, length);
    }
    static String[] splitPrimary(String absolute) {
        Matcher matcher = PRIMARY_ROOT.matcher(absolute);
        return matcher.matches() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
