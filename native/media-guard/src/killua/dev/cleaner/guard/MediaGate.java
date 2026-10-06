package killua.dev.cleaner.guard;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.ArrayMap;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
final class MediaGate implements InvocationHandler {
    static final Object PROCEED = new Object();
    private static final String RELATIVE_PATH = "relative_path";
    private static final String DATA = "_data";
    private static final String SCAN_FILE_CALL = "scan_file";

    private final Object provider;
    private final PolicyFeed feed;
    private final Method query;
    private final VirtualMedia virtual;

    private MediaGate(Object provider, PolicyFeed feed, Method query) {
        this.provider = provider;
        this.feed = feed;
        this.query = query;
        this.virtual = new VirtualMedia(feed, provider);
    }

    static void wrap(Object transport, PolicyFeed feed) throws ReflectiveOperationException {
        Field field = findField(transport.getClass(), "mInterface");
        Object original = field.get(transport);
        if (original == null || Proxy.isProxyClass(original.getClass())) {
            return;
        }
        Class<?> contentInterface = Class.forName("android.content.ContentInterface");
        Method query = contentInterface.getMethod(
                "query", Uri.class, String[].class, Bundle.class, CancellationSignal.class);
        field.set(transport, Proxy.newProxyInstance(
                contentInterface.getClassLoader(),
                new Class<?>[]{contentInterface},
                new MediaGate(original, feed, query)));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args != null) {
            int uid = Binder.getCallingUid();
            Object verdict;
            try {
                verdict = enforce(uid, feed.forUid(uid), method, args);
            } catch (FileNotFoundException | OperationApplicationException refused) {
                throw refused;
            } catch (Exception guardFailure) {
                // A guard bug must never take MediaStore down with it.
                Log.w(MediaGuard.TAG, "media guard: " + method.getName() + " fell back to MediaProvider", guardFailure);
                verdict = PROCEED;
            }
            if (verdict != PROCEED) {
                return verdict;
            }
        }
        return callProvider(method, args);
    }

    private Object callProvider(Method method, Object[] args) throws Exception {
        try {
            return method.invoke(provider, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private Object enforce(final int uid, ProviderPolicy policy, final Method method, final Object[] args)
            throws Exception {
        String name = method.getName();
        // Rows of isolated storage, also reachable by apps the owner granted a URI to.
        Object served = virtual.serveItem(uid, policy, name, args);
        if (served != PROCEED || policy == null) {
            return served;
        }
        boolean filters = policy.filtersProviders();
        switch (name) {
            case "insert": {
                Uri uri = (Uri) args[0];
                if (!filters || !MediaUris.isGuardedCollection(uri)) {
                    return PROCEED;
                }
                ContentValues original = copyOf((ContentValues) args[1]);
                ContentValues values = copyOf(original);
                if (!admitWrite(policy, uri, values, true)) {
                    try {
                        return virtual.insertIsolated(uid, policy, uri, original);
                    } catch (IOException failure) {
                        return null;
                    }
                }
                args[1] = values;
                return PROCEED;
            }
            case "delete": {
                Uri uri = (Uri) args[0];
                if (MediaUris.itemId(uri) >= 0 || !MediaUris.isGuardedCollection(uri)) {
                    return PROCEED;
                }
                String selection = null;
                String[] selectionArgs = null;
                if (args.length >= 3) {
                    selection = (String) args[1];
                    selectionArgs = (String[]) args[2];
                } else if (args.length == 2 && args[1] instanceof Bundle) {
                    Bundle extras = (Bundle) args[1];
                    selection = extras.getString(ContentResolver.QUERY_ARG_SQL_SELECTION);
                    selectionArgs = extras.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
                }
                int removed = virtual.deleteMatching(uid, policy, uri, selection, selectionArgs);
                if (removed == 0) {
                    return PROCEED;
                }
                Object real = callProvider(method, args);
                return removed + (real instanceof Integer ? (Integer) real : 0);
            }
            case "call": {
                if (args.length < 3 || !SCAN_FILE_CALL.equals(args[1]) || !(args[2] instanceof String)) {
                    return PROCEED;
                }
                Bundle scanned = virtual.scanFile(uid, policy, (String) args[2]);
                return scanned != null ? scanned : PROCEED;
            }
            default:
                break;
        }
        if (name.equals("query")) {
            Uri uri = (Uri) args[0];
            if (!MediaUris.isGuardedCollection(uri)) {
                return PROCEED;
            }
            Class<?>[] types = method.getParameterTypes();
            int bundleAt = -1;
            for (int i = 0; i < types.length; i++) {
                if (types[i] == Bundle.class) {
                    bundleAt = i;
                }
            }
            if (bundleAt < 0) {
                return PROCEED;
            }
            final int argsAt = bundleAt;
            Bundle original = (Bundle) args[argsAt];
            Bundle forReal = filters ? restrict(original, policy) : original;
            args[argsAt] = forReal;
            Cursor merged = virtual.query(uid, policy, uri, (String[]) args[1], original, forReal,
                    new VirtualMedia.RealQuery() {
                        @Override
                        public Cursor run(String[] projection, Bundle queryArgs) throws Exception {
                            Object[] realArgs = args.clone();
                            realArgs[1] = projection;
                            realArgs[argsAt] = queryArgs;
                            return (Cursor) callProvider(method, realArgs);
                        }
                    });
            return merged != null ? merged : PROCEED;
        }
        return filters ? enforceRule(policy, method, args) : PROCEED;
    }

    private Object enforceRule(ProviderPolicy policy, Method method, Object[] args) throws Exception {
        switch (method.getName()) {
            case "bulkInsert": {
                Uri uri = (Uri) args[0];
                ContentValues[] rows = (ContentValues[]) args[1];
                if (!MediaUris.isGuardedCollection(uri) || rows == null) {
                    return PROCEED;
                }
                List<ContentValues> admitted = new ArrayList<>(rows.length);
                for (ContentValues row : rows) {
                    ContentValues values = copyOf(row);
                    if (admitWrite(policy, uri, values, true)) {
                        admitted.add(values);
                    }
                }
                if (admitted.isEmpty()) {
                    return 0;
                }
                args[1] = admitted.toArray(new ContentValues[0]);
                return PROCEED;
            }
            case "update": {
                Uri uri = (Uri) args[0];
                ContentValues values = (ContentValues) args[1];
                if (!MediaUris.isGuardedCollection(uri) || values == null || !movesFile(values)) {
                    return PROCEED;
                }
                values = copyOf(values);
                if (!admitWrite(policy, uri, values, false)) {
                    return 0;
                }
                args[1] = values;
                return PROCEED;
            }
            case "openFile":
            case "openAssetFile":
            case "openTypedAssetFile": {
                Uri uri = (Uri) args[0];
                if (MediaUris.isGuardedItem(uri) && isHidden(policy, uri)) {
                    throw new FileNotFoundException(uri.toString());
                }
                return PROCEED;
            }
            case "applyBatch": {
                for (Object arg : args) {
                    if (arg instanceof List) {
                        for (Object operation : (List<?>) arg) {
                            if (operation instanceof ContentProviderOperation) {
                                admitOperation(policy, (ContentProviderOperation) operation);
                            }
                        }
                    }
                }
                return PROCEED;
            }
            default:
                return PROCEED;
        }
    }
    private static boolean admitWrite(ProviderPolicy policy, Uri uri, ContentValues values, boolean inserting) {
        String relative = values.getAsString(RELATIVE_PATH);
        String data = values.getAsString(DATA);
        if (relative == null && data == null) {
            if (!inserting) {
                return true;
            }
            String fallback = MediaUris.defaultFolder(uri);
            String target = policy.placeWrite(fallback);
            if (target == null) {
                return false;
            }
            if (!target.equals(fallback)) {
                values.put(RELATIVE_PATH, target + "/");
            }
            return true;
        }
        if (relative != null) {
            String dir = PathNames.normalize(relative);
            String target = dir == null ? null : policy.placeWrite(dir);
            if (target == null) {
                return false;
            }
            if (!target.equals(dir)) {
                values.put(RELATIVE_PATH, target + "/");
            }
        }
        if (data != null) {
            String[] parts = PathNames.splitPrimary(data);
            if (parts == null) {
                // Not on the primary emulated storage, which is all isolation covers.
                return true;
            }
            String rest = parts[1];
            int slash = rest.lastIndexOf('/');
            String dir = slash < 0 ? "" : PathNames.normalize(rest.substring(0, slash));
            String target = dir == null ? null : policy.placeWrite(dir);
            if (target == null) {
                return false;
            }
            if (!target.equals(dir)) {
                values.put(DATA, parts[0] + target + rest.substring(slash));
            }
        }
        return true;
    }

    private static Bundle restrict(Bundle original, ProviderPolicy policy) {
        Bundle args = original == null ? new Bundle() : new Bundle(original);
        List<String> guardArgs = new ArrayList<>();
        String guard = policy.visibilitySelection(guardArgs);
        String selection = args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION);
        String[] selectionArgs = args.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
        boolean hasSelection = selection != null && !selection.trim().isEmpty();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                hasSelection ? "(" + selection + ") AND " + guard : guard);
        List<String> merged = new ArrayList<>();
        if (selectionArgs != null) {
            Collections.addAll(merged, selectionArgs);
        }
        merged.addAll(guardArgs);
        args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, merged.toArray(new String[0]));
        return args;
    }
    private boolean isHidden(ProviderPolicy policy, Uri uri) throws Exception {
        Object result = query.invoke(provider, uri, new String[]{RELATIVE_PATH}, null, null);
        if (!(result instanceof Cursor)) {
            return false;
        }
        try (Cursor cursor = (Cursor) result) {
            return cursor.moveToFirst() && !policy.isVisibleRow(cursor.getString(0));
        }
    }
    @SuppressWarnings("unchecked")
    private static void admitOperation(ProviderPolicy policy, ContentProviderOperation operation)
            throws Exception {
        boolean inserting = operation.isInsert();
        if ((!inserting && !operation.isUpdate()) || !MediaUris.isGuardedCollection(operation.getUri())) {
            return;
        }
        // ContentValues up to Android 10, ArrayMap<String, Object> (with back references) since.
        Field field = findField(ContentProviderOperation.class, "mValues");
        Object stored = field.get(operation);
        ContentValues probe = new ContentValues();
        copyPathColumn(stored, probe, RELATIVE_PATH);
        copyPathColumn(stored, probe, DATA);
        if (!inserting && probe.size() == 0) {
            return;
        }
        if (!admitWrite(policy, operation.getUri(), probe, inserting)) {
            throw new OperationApplicationException("Refused by storage isolation: " + operation.getUri());
        }
        if (probe.size() == 0) {
            return;
        }
        if (stored instanceof ContentValues) {
            ((ContentValues) stored).putAll(probe);
        } else if (stored instanceof Map) {
            for (String key : probe.keySet()) {
                ((Map<String, Object>) stored).put(key, probe.getAsString(key));
            }
        } else if (stored == null) {
            if (field.getType().isAssignableFrom(ContentValues.class)) {
                field.set(operation, probe);
            } else if (field.getType().isAssignableFrom(ArrayMap.class)) {
                ArrayMap<String, Object> values = new ArrayMap<>();
                for (String key : probe.keySet()) {
                    values.put(key, probe.getAsString(key));
                }
                field.set(operation, values);
            }
        }
    }

    private static void copyPathColumn(Object stored, ContentValues probe, String key) {
        Object value = null;
        if (stored instanceof ContentValues) {
            value = ((ContentValues) stored).get(key);
        } else if (stored instanceof Map) {
            value = ((Map<?, ?>) stored).get(key);
        }
        if (value instanceof String) {
            probe.put(key, (String) value);
        }
    }

    private static boolean movesFile(ContentValues values) {
        return values.containsKey(RELATIVE_PATH) || values.containsKey(DATA);
    }

    private static ContentValues copyOf(ContentValues values) {
        return values == null ? new ContentValues() : new ContentValues(values);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {

            }
        }
        throw new NoSuchFieldException(name);
    }
}
