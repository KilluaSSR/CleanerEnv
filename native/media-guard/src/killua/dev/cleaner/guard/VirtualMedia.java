package killua.dev.cleaner.guard;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.util.Size;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shows each isolated app the files of its own isolated storage through MediaStore, next to
 * the real rows it may see. MediaProvider never indexes {@code Android/data}, so these rows
 * only exist here: ids come from a reserved range, and every operation on them is served
 * from the isolated file directly.
 */
final class VirtualMedia implements IsolatedIndex.Ids {
    /** Above any real MediaStore id in practice, yet within int range for apps using getInt. */
    static final long FIRST_ID = 1_500_000_000L;
    private static final long ID_SPAN = 0x1FFF_FFFFL;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int DIR_MODE = 0777;
    private static final int FILE_MODE = 0666;

    interface RealQuery {
        Cursor run(String[] projection, Bundle args) throws Exception;
    }

    private final PolicyFeed feed;
    private final Object provider;
    private final ConcurrentHashMap<Long, MediaEntry> byId = new ConcurrentHashMap<>();
    private final Map<Long, String> claims = new HashMap<>();
    private final Map<Integer, IsolatedIndex> indexes = new HashMap<>();
    private Handler closeHandler;

    VirtualMedia(PolicyFeed feed, Object provider) {
        this.feed = feed;
        this.provider = provider;
    }

    // region Ids

    @Override
    public long assign(int uid, String viewPath) {
        String key = uid + ":" + viewPath;
        synchronized (claims) {
            long id = FIRST_ID + ((((long) uid) * 31 + viewPath.hashCode()) & 0x7FFF_FFFFL) % ID_SPAN;
            while (true) {
                String holder = claims.get(id);
                if (holder == null) {
                    claims.put(id, key);
                    return id;
                }
                if (holder.equals(key)) {
                    return id;
                }
                id = id + 1 >= FIRST_ID + ID_SPAN ? FIRST_ID : id + 1;
            }
        }
    }

    @Override
    public void bind(MediaEntry entry) {
        synchronized (claims) {
            claims.put(entry.id, entry.ownerUid + ":" + entry.viewPath);
        }
        byId.put(entry.id, entry);
    }

    @Override
    public void release(MediaEntry entry) {
        synchronized (claims) {
            claims.remove(entry.id);
        }
        byId.remove(entry.id);
    }

    // endregion

    /** Merged result of a collection query, or {@code null} to run the real query untouched. */
    Cursor query(int uid, ProviderPolicy policy, Uri uri, String[] projection, Bundle original,
                 Bundle forReal, RealQuery real) throws Exception {
        String kind = kindClause(uri);
        if (kind == null || MediaUris.itemId(uri) >= 0) {
            return null;
        }
        if (projection != null) {
            for (String column : projection) {
                if (column == null || !IDENTIFIER.matcher(column).matches()) {
                    return null;
                }
            }
        }
        QueryShape shape = QueryShape.parse(uri, original);
        if (shape == null) {
            return null;
        }
        List<MediaEntry> entries = indexFor(uid).select(policy, kind, shape.selection,
                shape.selectionArgs, shape.sort, shape.window(), needsDetails(projection, shape));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        SortKeys keys = SortKeys.parse(shape.sort);
        String[] realProjection = projection;
        if (projection != null && keys != null) {
            List<String> widened = new ArrayList<>();
            Collections.addAll(widened, projection);
            for (String column : keys.columns) {
                if (!containsIgnoreCase(widened, column)) {
                    widened.add(column);
                }
            }
            realProjection = widened.toArray(new String[0]);
        }
        Cursor cursor = real.run(realProjection, shape.rewrite(forReal));
        return MergedCursor.merge(cursor, projection, entries, keys, shape.offset, shape.limit);
    }

    /** Serves an operation on one of our ids; {@link MediaGate#PROCEED} for anything else. */
    @SuppressWarnings("deprecation")
    Object serveItem(int uid, ProviderPolicy policy, String name, Object[] args) throws Exception {
        if (args.length == 0 || !(args[0] instanceof Uri)) {
            return MediaGate.PROCEED;
        }
        Uri uri = (Uri) args[0];
        long id = MediaUris.itemId(uri);
        if (id < FIRST_ID) {
            return MediaGate.PROCEED;
        }
        boolean opensForWrite = (name.equals("openFile") || name.equals("openAssetFile"))
                && args.length > 1 && args[1] instanceof String && ((String) args[1]).contains("w");
        boolean writes = name.equals("delete") || name.equals("update") || opensForWrite;
        MediaEntry entry = lookup(uid, policy, id);
        if (entry == null || !mayAccess(uid, entry, uri, writes)) {
            return MediaGate.PROCEED;
        }
        switch (name) {
            case "getType":
                return entry.mime;
            case "query":
                return itemCursor(entry, (String[]) args[1], uri);
            case "openFile":
                return open(entry, (String) args[1]);
            case "openAssetFile":
                return new AssetFileDescriptor(open(entry, (String) args[1]), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            case "openTypedAssetFile": {
                Bundle opts = args.length > 2 && args[2] instanceof Bundle ? (Bundle) args[2] : null;
                Object size = opts == null ? null : opts.get(ContentResolver.EXTRA_SIZE);
                CancellationSignal signal = args.length > 3 && args[3] instanceof CancellationSignal
                        ? (CancellationSignal) args[3] : null;
                if (size instanceof Point) {
                    return thumbnail(entry, (Point) size, signal);
                }
                return new AssetFileDescriptor(open(entry, "r"), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            }
            case "delete":
                return delete(entry);
            case "update":
                return args.length > 1 && args[1] instanceof ContentValues
                        ? update(entry, (ContentValues) args[1]) : 0;
            default:
                return MediaGate.PROCEED;
        }
    }

    /**
     * Creates the file an app inserts into a folder its view keeps isolated, instead of a real
     * row it could never read back. {@code null} when the target is not isolated storage.
     */
    Uri insertIsolated(int uid, ProviderPolicy policy, Uri collection, ContentValues values) throws IOException {
        String data = values.getAsString("_data");
        String dir;
        String name;
        boolean exact = false;
        if (data != null) {
            String[] parts = PathNames.splitPrimary(data);
            String rest = parts == null ? null : PathNames.normalize(parts[1]);
            if (rest == null || rest.isEmpty()) {
                return null;
            }
            int slash = rest.lastIndexOf('/');
            dir = slash < 0 ? "" : rest.substring(0, slash);
            name = rest.substring(slash + 1);
            exact = true;
        } else {
            String relative = values.getAsString("relative_path");
            dir = relative != null ? PathNames.normalize(relative) : MediaUris.defaultFolder(collection);
            name = completeName(values.getAsString("_display_name"), values.getAsString("mime_type"));
        }
        if (dir == null || name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        File folder = IsolatedIndex.lowerPathFor(policy, dir);
        if (folder == null || !makeDirs(folder)) {
            return null;
        }
        File target = new File(folder, name);
        if (!exact) {
            target = unique(folder, name);
        }
        if (!target.exists()) {
            new FileOutputStream(target).close();
            chmod(target, FILE_MODE);
        }
        String viewPath = dir.isEmpty() ? target.getName() : dir + "/" + target.getName();
        MediaEntry entry = indexFor(uid).register(policy, viewPath, target);
        notifyChanged(entry);
        return ContentUris.withAppendedId(collection.buildUpon().clearQuery().fragment(null).build(), entry.id);
    }

    /** Deletes isolated rows a collection delete selects; real rows are left to MediaProvider. */
    int deleteMatching(int uid, ProviderPolicy policy, Uri uri, String selection, String[] selectionArgs) {
        String kind = kindClause(uri);
        // Never treat a selection-less delete as "wipe the app's isolated media".
        if (kind == null || selection == null || selection.trim().isEmpty()) {
            return 0;
        }
        List<MediaEntry> matched = indexFor(uid).select(policy, kind, selection, selectionArgs, null, -1, false);
        int deleted = 0;
        if (matched != null) {
            for (MediaEntry entry : matched) {
                deleted += delete(entry);
            }
        }
        return deleted;
    }

    /**
     * {@code MediaStore.scanFile} of a path the app's view keeps isolated: MediaProvider would
     * scan the real path behind the same name. {@code null} for real paths.
     */
    Bundle scanFile(int uid, ProviderPolicy policy, String path) {
        String[] parts = path == null ? null : PathNames.splitPrimary(path);
        String viewPath = parts == null ? null : PathNames.normalize(parts[1]);
        if (viewPath == null || viewPath.isEmpty()) {
            return null;
        }
        File file = IsolatedIndex.lowerPathFor(policy, viewPath);
        if (file == null) {
            return null;
        }
        IsolatedIndex index = indexFor(uid);
        index.invalidate();
        Bundle out = new Bundle();
        MediaEntry entry = null;
        if (file.isFile()) {
            entry = index.find(policy, viewPath);
            if (entry == null) {
                entry = index.find(policy, IsolatedIndex.shownPath(policy, viewPath));
            }
        }
        if (entry != null) {
            out.putParcelable(Intent.EXTRA_STREAM, itemUri(entry));
            notifyChanged(entry);
        }
        return out;
    }

    private IsolatedIndex indexFor(int uid) {
        synchronized (indexes) {
            IsolatedIndex index = indexes.get(uid);
            if (index == null) {
                index = new IsolatedIndex(uid, this);
                indexes.put(uid, index);
            }
            return index;
        }
    }

    private MediaEntry lookup(int uid, ProviderPolicy policy, long id) {
        MediaEntry entry = byId.get(id);
        if (entry == null && policy != null) {
            // Ids survive in apps across a MediaProvider restart; rebuild the caller's index.
            indexFor(uid).rescan(policy);
            entry = byId.get(id);
        }
        if (entry == null || feed.forUid(entry.ownerUid) == null) {
            return null;
        }
        if (!entry.file.isFile()) {
            indexFor(entry.ownerUid).forget(entry);
            return null;
        }
        return entry;
    }

    /** The owner always; anyone else only with a URI grant, e.g. from a share intent. */
    private boolean mayAccess(int uid, MediaEntry entry, Uri uri, boolean write) {
        if (uid == entry.ownerUid) {
            return true;
        }
        Context context = context();
        int mode = write ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : Intent.FLAG_GRANT_READ_URI_PERMISSION;
        return context != null && context.checkUriPermission(uri, Binder.getCallingPid(), uid, mode)
                == PackageManager.PERMISSION_GRANTED;
    }

    private Cursor itemCursor(MediaEntry entry, String[] projection, Uri uri) {
        String[] columns = projection != null ? projection : MediaEntry.COLUMNS;
        if (needsDetails(columns, null)) {
            entry.loadDetails();
        }
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            row[i] = columns[i] == null ? null : entry.value(columns[i]);
        }
        cursor.addRow(row);
        Context context = context();
        if (context != null) {
            cursor.setNotificationUri(context.getContentResolver(), uri);
        }
        return cursor;
    }

    private ParcelFileDescriptor open(final MediaEntry entry, String mode) throws FileNotFoundException {
        if (!mode.contains("w")) {
            return ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        try {
            return ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.parseMode(mode), closeHandler(),
                    new ParcelFileDescriptor.OnCloseListener() {
                        @Override
                        public void onClose(IOException failure) {
                            indexFor(entry.ownerUid).invalidate();
                            notifyChanged(entry);
                        }
                    });
        } catch (IOException failure) {
            throw new FileNotFoundException(failure.getMessage());
        }
    }

    private AssetFileDescriptor thumbnail(MediaEntry entry, Point size, CancellationSignal signal)
            throws FileNotFoundException {
        Context context = context();
        if (context == null) {
            return new AssetFileDescriptor(open(entry, "r"), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        Size target = new Size(Math.max(size.x, 1), Math.max(size.y, 1));
        File cache = null;
        try {
            Bitmap bitmap;
            switch (entry.mediaType) {
                case MediaEntry.TYPE_IMAGE:
                    bitmap = ThumbnailUtils.createImageThumbnail(entry.file, target, signal);
                    break;
                case MediaEntry.TYPE_VIDEO:
                    bitmap = ThumbnailUtils.createVideoThumbnail(entry.file, target, signal);
                    break;
                case MediaEntry.TYPE_AUDIO:
                    bitmap = ThumbnailUtils.createAudioThumbnail(entry.file, target, signal);
                    break;
                default:
                    throw new FileNotFoundException("no thumbnail for " + entry.mime);
            }
            cache = File.createTempFile("cleaner-thumb", ".jpg", context.getCacheDir());
            try (FileOutputStream out = new FileOutputStream(cache)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            bitmap.recycle();
            return new AssetFileDescriptor(ParcelFileDescriptor.open(cache, ParcelFileDescriptor.MODE_READ_ONLY),
                    0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (FileNotFoundException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new FileNotFoundException(failure.getMessage());
        } finally {
            // The open descriptor keeps the unlinked file alive.
            if (cache != null && !cache.delete()) {
                cache.deleteOnExit();
            }
        }
    }

    private int delete(MediaEntry entry) {
        boolean gone = entry.file.delete() || !entry.file.exists();
        indexFor(entry.ownerUid).forget(entry);
        notifyChanged(entry);
        return gone ? 1 : 0;
    }

    /** Renames and moves stay inside isolated storage; flags such as is_pending have no effect. */
    private int update(MediaEntry entry, ContentValues values) {
        String data = values.getAsString("_data");
        String relative = values.getAsString("relative_path");
        String displayName = values.getAsString("_display_name");
        if (data == null && relative == null && displayName == null) {
            return 1;
        }
        String dir;
        String name;
        if (data != null) {
            String[] parts = PathNames.splitPrimary(data);
            String rest = parts == null ? null : PathNames.normalize(parts[1]);
            if (rest == null || rest.isEmpty()) {
                return 0;
            }
            int slash = rest.lastIndexOf('/');
            dir = slash < 0 ? "" : rest.substring(0, slash);
            name = rest.substring(slash + 1);
        } else {
            dir = relative != null ? PathNames.normalize(relative) : entry.viewDir();
            name = displayName != null ? displayName.replace('/', '_') : entry.name;
        }
        ProviderPolicy owner = feed.forUid(entry.ownerUid);
        if (dir == null || name.isEmpty() || name.equals(".") || name.equals("..") || owner == null) {
            return 0;
        }
        File folder = IsolatedIndex.lowerPathFor(owner, dir);
        if (folder == null || !makeDirs(folder)) {
            return 0;
        }
        File target = new File(folder, name);
        if (target.equals(entry.file)) {
            return 1;
        }
        if (target.exists() || !entry.file.renameTo(target)) {
            return 0;
        }
        notifyChanged(entry);
        MediaEntry moved = indexFor(entry.ownerUid).move(owner, entry, dir.isEmpty() ? name : dir + "/" + name, target);
        notifyChanged(moved);
        return 1;
    }

    private static String kindClause(Uri uri) {
        if (!MediaUris.isGuardedCollection(uri)) {
            return null;
        }
        switch (uri.getPathSegments().get(1)) {
            case "images":
                return "media_type=" + MediaEntry.TYPE_IMAGE;
            case "video":
                return "media_type=" + MediaEntry.TYPE_VIDEO;
            case "audio":
                return "media_type=" + MediaEntry.TYPE_AUDIO;
            case "downloads":
                return "is_download=1";
            default:
                return "1";
        }
    }

    private static boolean needsDetails(String[] projection, QueryShape shape) {
        if (projection == null) {
            return true;
        }
        for (String column : projection) {
            if (column != null && MediaEntry.DETAIL_COLUMNS.contains(column.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        if (shape != null) {
            String clauses = ((shape.selection == null ? "" : shape.selection) + " "
                    + (shape.sort == null ? "" : shape.sort)).toLowerCase(Locale.ROOT);
            for (String column : MediaEntry.DETAIL_COLUMNS) {
                if (clauses.contains(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> columns, String column) {
        for (String existing : columns) {
            if (existing.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private static String completeName(String displayName, String mime) {
        String name = displayName == null || displayName.trim().isEmpty()
                ? String.valueOf(System.currentTimeMillis()) : displayName.trim().replace('/', '_');
        if (mime == null || MediaEntry.mimeOf(name).equalsIgnoreCase(mime)) {
            return name;
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.toLowerCase(Locale.ROOT));
        return extension == null ? name : name + "." + extension;
    }

    /** MediaProvider's naming for clashes: {@code name (1).ext}. */
    private static File unique(File folder, String name) {
        File candidate = new File(folder, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; candidate.exists(); i++) {
            candidate = new File(folder, base + " (" + i + ")" + extension);
        }
        return candidate;
    }

    /**
     * MediaProvider owns what it creates here; the app reaches it through the package dir's
     * default ACL where present and through the mode bits otherwise. The package dir itself
     * still keeps every other app out.
     */
    private static boolean makeDirs(File folder) {
        if (folder.isDirectory()) {
            return true;
        }
        File parent = folder.getParentFile();
        if (parent == null || !makeDirs(parent)) {
            return false;
        }
        if (!folder.mkdir() && !folder.isDirectory()) {
            return false;
        }
        chmod(folder, DIR_MODE);
        return true;
    }

    private static void chmod(File file, int mode) {
        try {
            Os.chmod(file.getPath(), mode);
        } catch (Exception ignored) {
            // The inherited default ACL may already grant the app access.
        }
    }

    private static Uri itemUri(MediaEntry entry) {
        return ContentUris.withAppendedId(collectionUri("external_primary", entry.mediaType), entry.id);
    }

    private static Uri collectionUri(String volume, int mediaType) {
        String path;
        switch (mediaType) {
            case MediaEntry.TYPE_IMAGE:
                path = "images/media";
                break;
            case MediaEntry.TYPE_VIDEO:
                path = "video/media";
                break;
            case MediaEntry.TYPE_AUDIO:
                path = "audio/media";
                break;
            default:
                path = "file";
                break;
        }
        return Uri.parse("content://media/" + volume + "/" + path);
    }

    private void notifyChanged(MediaEntry entry) {
        Context context = context();
        if (context == null) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        for (String volume : new String[]{"external", "external_primary"}) {
            resolver.notifyChange(Uri.parse("content://media/" + volume + "/file"), null);
            if (entry.mediaType != MediaEntry.TYPE_NONE) {
                resolver.notifyChange(collectionUri(volume, entry.mediaType), null);
            }
            if (Long.valueOf(1L).equals(entry.value("is_download"))) {
                resolver.notifyChange(Uri.parse("content://media/" + volume + "/downloads"), null);
            }
        }
    }

    private Context context() {
        return provider instanceof ContentProvider ? ((ContentProvider) provider).getContext() : null;
    }

    private synchronized Handler closeHandler() {
        if (closeHandler == null) {
            HandlerThread thread = new HandlerThread("cleaner-media-close");
            thread.start();
            closeHandler = new Handler(thread.getLooper());
        }
        return closeHandler;
    }
}
