package killua.dev.cleaner.guard;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IsolatedIndex {
    interface Ids {
        long assign(int uid, String viewPath);

        void bind(MediaEntry entry);

        void release(MediaEntry entry);
    }

    private static final long FRESH_MS = 2_000L;
    private static final long DETAIL_BUDGET_MS = 800L;
    private static final int MAX_ENTRIES = 20_000;
    private static final int MAX_DEPTH = 32;

    private final int uid;
    private final Ids ids;
    private Map<String, MediaEntry> byPath = new HashMap<>();
    private long refreshedAt;
    private boolean stale = true;
    private int version;
    private int tableVersion = -1;
    private SQLiteDatabase table;

    IsolatedIndex(int uid, Ids ids) {
        this.uid = uid;
        this.ids = ids;
    }

    synchronized void invalidate() {
        stale = true;
    }

    synchronized MediaEntry find(ProviderPolicy policy, String viewPath) {
        refresh(policy);
        return byPath.get(viewPath);
    }

    synchronized void rescan(ProviderPolicy policy) {
        stale = true;
        refresh(policy);
    }

    /**
     * Entries matching an app's selection in the app's order, at most {@code window} of them
     * ({@code < 0}: all). {@code null} when the selection cannot run against these rows.
     */
    synchronized List<MediaEntry> select(ProviderPolicy policy, String kind, String selection,
                                         String[] selectionArgs, String sort, long window,
                                         boolean details) {
        refresh(policy);
        if (byPath.isEmpty()) {
            return Collections.emptyList();
        }
        if (details) {
            loadDetails();
        }
        StringBuilder sql = new StringBuilder("SELECT _id FROM files WHERE (").append(kind).append(')');
        if (selection != null && !selection.trim().isEmpty()) {
            sql.append(" AND (").append(selection).append(')');
        }
        sql.append(" ORDER BY ").append(sort == null || sort.trim().isEmpty() ? "_id" : sort);
        if (window >= 0) {
            sql.append(" LIMIT ").append(window);
        }
        Map<Long, MediaEntry> byId = new HashMap<>();
        for (MediaEntry entry : byPath.values()) {
            byId.put(entry.id, entry);
        }
        List<MediaEntry> matched = new ArrayList<>();
        try (Cursor cursor = table().rawQuery(sql.toString(), selectionArgs)) {
            while (cursor.moveToNext()) {
                MediaEntry entry = byId.get(cursor.getLong(0));
                if (entry != null) {
                    matched.add(entry);
                }
            }
        } catch (SQLiteException | IllegalArgumentException inapplicable) {
            return null;
        }
        return matched;
    }

    /** Lower path behind {@code viewPath} if the app's view shows isolated storage there. */
    static File lowerPathFor(ProviderPolicy policy, String viewPath) {
        List<String[]> sources = policy.isolatedSources();
        for (String[] source : sources) {
            if (source[2] != null && PathNames.within(viewPath, source[2])) {
                String rest = viewPath.substring(source[2].length());
                return rest.isEmpty() ? new File(source[1]) : new File(source[1], rest.substring(1));
            }
        }
        String[] best = null;
        for (String[] source : sources) {
            if (contains(source[0], viewPath) && (best == null || source[0].length() > best[0].length())) {
                best = source;
            }
        }
        if (best == null || isMasked(viewPath, best[0], policy.boundFolders())) {
            return null;
        }
        String rest = viewPath.substring(best[0].length());
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        return rest.isEmpty() ? new File(best[1]) : new File(best[1], rest);
    }

    synchronized MediaEntry register(ProviderPolicy policy, String viewPath, File file) {
        refresh(policy);
        MediaEntry entry = byPath.get(viewPath);
        String shown = shownPath(policy, viewPath);
        if (entry == null) {
            entry = new MediaEntry(ids.assign(uid, viewPath), uid, policy.ownerPackage(), policy.user,
                    file, viewPath, shown, file.length(), file.lastModified());
        } else {
            entry = entry.with(file, viewPath, shown, file.length(), file.lastModified());
        }
        put(entry);
        return entry;
    }

    synchronized MediaEntry move(ProviderPolicy policy, MediaEntry entry, String viewPath, File file) {
        byPath.remove(entry.viewPath);
        MediaEntry moved = entry.with(file, viewPath, shownPath(policy, viewPath), file.length(), file.lastModified());
        put(moved);
        return moved;
    }

    static String shownPath(ProviderPolicy policy, String viewPath) {
        for (String[] source : policy.isolatedSources()) {
            if (source[2] != null && PathNames.within(viewPath, source[2]) && viewPath.length() > source[2].length()) {
                return viewPath.substring(source[2].length() + 1);
            }
        }
        return viewPath;
    }

    synchronized void forget(MediaEntry entry) {
        if (byPath.remove(entry.viewPath) != null) {
            ids.release(entry);
            version++;
        }
    }

    private void put(MediaEntry entry) {
        byPath.put(entry.viewPath, entry);
        ids.bind(entry);
        version++;
    }

    private void refresh(ProviderPolicy policy) {
        long now = SystemClock.uptimeMillis();
        if (!stale && now - refreshedAt < FRESH_MS) {
            return;
        }
        Map<String, MediaEntry> next = new HashMap<>();
        boolean[] changed = {false};
        List<String> bound = policy.boundFolders();
        for (String[] source : policy.isolatedSources()) {
            walk(policy, source[0], source[2], new File(source[1]), bound, next, changed);
        }
        for (MediaEntry old : byPath.values()) {
            if (!next.containsKey(old.viewPath)) {
                ids.release(old);
                changed[0] = true;
            }
        }
        byPath = next;
        if (changed[0]) {
            version++;
        }
        stale = false;
        refreshedAt = now;
    }


    private void walk(final ProviderPolicy policy, final String sourceView, final String privateRoot,
                      File root, final List<String> bound, final Map<String, MediaEntry> next,
                      final boolean[] changed) {
        final Path start = root.toPath();
        try {
            Files.walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), MAX_DEPTH,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (dir.equals(start)) {
                                return FileVisitResult.CONTINUE;
                            }
                            String shown = viewOf(start, dir, sourceView);
                            if (dir.getFileName().toString().startsWith(".")
                                    || (isMasked(shown, sourceView, bound) && !reachable(shown))
                                    || new File(dir.toFile(), ".nomedia").exists()) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile() || path.getFileName().toString().startsWith(".")) {
                                return FileVisitResult.CONTINUE;
                            }
                            String shown = viewOf(start, path, sourceView);
                            String viewPath = shown;
                            if (isMasked(shown, sourceView, bound)) {
                                if (!reachable(shown)) {
                                    return FileVisitResult.CONTINUE;
                                }
                                viewPath = privateRoot + "/" + start.relativize(path);
                            }
                            long size = attrs.size();
                            long modified = attrs.lastModifiedTime().toMillis();
                            File file = path.toFile();
                            MediaEntry old = byPath.get(viewPath);
                            MediaEntry entry;
                            if (old == null) {
                                entry = new MediaEntry(ids.assign(uid, viewPath), uid, policy.ownerPackage(),
                                        policy.user, file, viewPath, shown, size, modified);
                            } else if (old.sameStat(size, modified) && old.file.equals(file)
                                    && old.shownPath.equals(shown)) {
                                entry = old;
                            } else {
                                entry = old.with(file, viewPath, shown, size, modified);
                            }
                            if (entry != old) {
                                ids.bind(entry);
                                changed[0] = true;
                            }
                            next.put(viewPath, entry);
                            return next.size() >= MAX_ENTRIES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path path, IOException failure) {
                            return FileVisitResult.CONTINUE;
                        }

                        private boolean reachable(String shown) {
                            return privateRoot != null && !PathNames.within(shown, "Android");
                        }
                    });
        } catch (IOException | RuntimeException unreadable) {
            // A missing or unreadable source simply contributes nothing.
        }
    }

    private void loadDetails() {
        long start = SystemClock.uptimeMillis();
        boolean loaded = false;
        for (MediaEntry entry : byPath.values()) {
            if (entry.isDetailed()) {
                continue;
            }
            // Over budget, the rest keeps zero dimensions until a later query decodes it.
            if (SystemClock.uptimeMillis() - start > DETAIL_BUDGET_MS) {
                break;
            }
            entry.loadDetails();
            loaded = true;
        }
        if (loaded) {
            version++;
        }
    }

    private SQLiteDatabase table() {
        if (table == null) {
            table = SQLiteDatabase.create(null);
            StringBuilder schema = new StringBuilder("CREATE TABLE files (");
            for (String column : MediaEntry.COLUMNS) {
                schema.append(column).append(MediaEntry.TEXT_COLUMNS.contains(column) ? " TEXT," : " INTEGER,");
            }
            schema.setCharAt(schema.length() - 1, ')');
            table.execSQL(schema.toString());
        }
        if (tableVersion != version) {
            StringBuilder insert = new StringBuilder("INSERT INTO files VALUES (");
            for (int i = 0; i < MediaEntry.COLUMNS.length; i++) {
                insert.append(i == 0 ? "?" : ",?");
            }
            insert.append(')');
            table.beginTransaction();
            try (SQLiteStatement statement = table.compileStatement(insert.toString())) {
                table.execSQL("DELETE FROM files");
                for (MediaEntry entry : byPath.values()) {
                    statement.clearBindings();
                    for (int i = 0; i < MediaEntry.COLUMNS.length; i++) {
                        Object value = entry.value(MediaEntry.COLUMNS[i]);
                        if (value instanceof Long) {
                            statement.bindLong(i + 1, (Long) value);
                        } else if (value instanceof String) {
                            statement.bindString(i + 1, (String) value);
                        } else {
                            statement.bindNull(i + 1);
                        }
                    }
                    statement.executeInsert();
                }
                table.setTransactionSuccessful();
            } finally {
                table.endTransaction();
            }
            tableVersion = version;
        }
        return table;
    }

    private static String viewOf(Path start, Path path, String sourceView) {
        String relative = start.relativize(path).toString();
        return sourceView.isEmpty() ? relative : sourceView + "/" + relative;
    }

    /** A bind mount deeper than the source covers {@code viewPath}. */
    private static boolean isMasked(String viewPath, String sourceView, List<String> bound) {
        for (String folder : bound) {
            if (PathNames.within(viewPath, folder) && !contains(folder, sourceView)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String ancestor, String path) {
        return ancestor.isEmpty() || PathNames.within(path, ancestor);
    }
}
