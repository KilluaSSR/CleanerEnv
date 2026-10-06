package killua.dev.cleaner.guard;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
final class ProviderPolicy {
    private static final String[] GALLERY_FOLDERS = {"DCIM", "Pictures", "Movies"};
    private static final int PER_USER_RANGE = 100_000;

    final Set<String> packages = new LinkedHashSet<>();
    final int user;
    private boolean providers;
    private boolean gallery;
    private final List<String> roots = new ArrayList<>();
    private final List<String> accessible = new ArrayList<>();
    private final List<String> exportViews = new ArrayList<>();
    private final List<String> exportReals = new ArrayList<>();
    private final List<String> sharedViews = new ArrayList<>();
    private final List<String> sharedSources = new ArrayList<>();

    private ProviderPolicy(int uid) {
        this.user = uid / PER_USER_RANGE;
    }

    static Map<Integer, ProviderPolicy> decode(String text) {
        Map<Integer, ProviderPolicy> byUid = new HashMap<>();
        ProviderPolicy current = null;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq);
            String value = line.substring(eq + 1);
            if (key.equals("uid")) {
                current = null;
                try {
                    int uid = Integer.parseInt(value);
                    current = byUid.get(uid);
                    if (current == null) {
                        current = new ProviderPolicy(uid);
                        byUid.put(uid, current);
                    }
                } catch (NumberFormatException ignored) {

                }
                continue;
            }
            if (current == null) {
                continue;
            }
            switch (key) {
                case "pkg":
                    current.packages.add(value);
                    break;
                case "providers":
                    current.providers |= value.equals("1");
                    break;
                case "gallery":
                    current.gallery |= value.equals("1");
                    break;
                case "root": {
                    String path = PathNames.normalize(value);
                    if (path != null && !path.isEmpty()) {
                        current.roots.add(path);
                    }
                    break;
                }
                case "accessible": {
                    String path = PathNames.normalize(value);
                    if (path != null) {
                        current.accessible.add(path);
                    }
                    break;
                }
                case "export": {
                    String[] pair = pair(value);
                    if (pair != null) {
                        current.exportViews.add(pair[0]);
                        current.exportReals.add(pair[1]);
                    }
                    break;
                }
                case "shared": {
                    String[] pair = pair(value);
                    if (pair != null) {
                        current.sharedViews.add(pair[0]);
                        current.sharedSources.add(pair[1]);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        return byUid;
    }

    private static String[] pair(String value) {
        int tab = value.indexOf('\t');
        String first = tab > 0 ? PathNames.normalize(value.substring(0, tab)) : null;
        String second = tab > 0 ? PathNames.normalize(value.substring(tab + 1)) : null;
        return first == null || second == null || first.isEmpty() || second.isEmpty()
                ? null : new String[]{first, second};
    }

    /** Whether MediaStore results and writes of this app follow its rule. */
    boolean filtersProviders() {
        return providers;
    }

    String placeWrite(String dir) {
        // An export view is bound over whatever folder contains it, so it wins.
        for (int i = 0; i < exportViews.size(); i++) {
            String view = exportViews.get(i);
            if (PathNames.within(dir, view)) {
                return exportReals.get(i) + dir.substring(view.length());
            }
        }
        return isVisible(dir) ? dir : null;
    }

    boolean isVisible(String dir) {
        for (String own : ownFolders()) {
            if (PathNames.within(dir, own)) {
                return true;
            }
        }
        for (String folder : visibleFolders()) {
            if (PathNames.within(dir, folder)) {
                return true;
            }
        }
        return false;
    }

    boolean isVisibleRow(String relativePath) {
        String dir = relativePath == null ? null : PathNames.normalize(relativePath);
        return dir != null && isVisible(dir);
    }

    String visibilitySelection(List<String> args) {
        StringBuilder selection = new StringBuilder("(");
        List<String> folders = new ArrayList<>(visibleFolders());
        folders.addAll(ownFolders());
        for (String folder : folders) {
            selection.append("relative_path LIKE ? ESCAPE '\\' OR ");
            args.add(PathNames.escapeLike(folder) + "/%");
        }
        if (selection.length() == 1) {
            return "(0)";
        }
        selection.setLength(selection.length() - " OR ".length());
        return selection.append(')').toString();
    }


    List<String[]> isolatedSources() {
        String lowerBase = lowerBase();
        List<String[]> sources = new ArrayList<>();
        for (String root : roots) {
            sources.add(new String[]{"", lowerBase + "/" + root, root});
        }
        for (int i = 0; i < sharedViews.size(); i++) {
            String source = lowerBase + "/" + sharedSources.get(i);
            if (new File(source).isDirectory()) {
                sources.add(new String[]{sharedViews.get(i), source, null});
            }
        }
        return sources;
    }

    /** View paths a bind mount covers; isolated files beneath them are out of the app's sight. */
    List<String> boundFolders() {
        String lowerBase = lowerBase();
        List<String> bound = new ArrayList<>();
        bound.add("Android");
        bound.addAll(accessible);
        bound.addAll(exportViews);
        if (gallery) {
            for (String folder : GALLERY_FOLDERS) {
                if (new File(lowerBase + "/" + folder).isDirectory()) {
                    bound.add(folder);
                }
            }
        }
        for (int i = 0; i < sharedViews.size(); i++) {
            if (new File(lowerBase + "/" + sharedSources.get(i)).isDirectory()) {
                bound.add(sharedViews.get(i));
            }
        }
        return bound;
    }

    String ownerPackage() {
        return packages.isEmpty() ? null : packages.iterator().next();
    }

    private String lowerBase() {
        return "/data/media/" + user;
    }

    private List<String> visibleFolders() {
        List<String> folders = new ArrayList<>(accessible);
        folders.addAll(exportReals);
        if (gallery) {
            for (String folder : GALLERY_FOLDERS) {
                folders.add(folder);
            }
        }
        return folders;
    }

    private List<String> ownFolders() {
        List<String> folders = new ArrayList<>();
        for (String owner : packages) {
            folders.add("Android/data/" + owner);
            folders.add("Android/media/" + owner);
        }
        return folders;
    }
}
