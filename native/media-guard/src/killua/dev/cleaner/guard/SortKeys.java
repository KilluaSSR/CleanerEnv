package killua.dev.cleaner.guard;

import android.database.Cursor;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A plain {@code column [COLLATE x] [ASC|DESC], ...} order, compared the way SQLite does. */
final class SortKeys {
    private static final Pattern TERM = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?:COLLATE\\s+([A-Za-z_]+)\\s*)?(ASC|DESC)?\\s*$");
    private static final int BINARY = 0;
    private static final int NOCASE = 1;
    private static final int LOCALIZED = 2;

    final String[] columns;
    private final boolean[] descending;
    private final int[] collations;
    private final Collator collator = Collator.getInstance();

    private SortKeys(String[] columns, boolean[] descending, int[] collations) {
        this.columns = columns;
        this.descending = descending;
        this.collations = collations;
    }

    /** {@code null} when there is no order, or one too complex to replay. */
    static SortKeys parse(String sort) {
        if (sort == null || sort.trim().isEmpty() || sort.indexOf('(') >= 0) {
            return null;
        }
        List<String> columns = new ArrayList<>();
        List<Boolean> descending = new ArrayList<>();
        List<Integer> collations = new ArrayList<>();
        for (String term : sort.split(",")) {
            Matcher matcher = TERM.matcher(term);
            if (!matcher.matches()) {
                return null;
            }
            columns.add(matcher.group(1));
            String collation = matcher.group(2) == null ? "" : matcher.group(2).toUpperCase(Locale.ROOT);
            collations.add(collation.equals("NOCASE") ? NOCASE
                    : collation.equals("LOCALIZED") || collation.equals("UNICODE") ? LOCALIZED : BINARY);
            descending.add(matcher.group(3) != null && matcher.group(3).equalsIgnoreCase("DESC"));
        }
        boolean[] desc = new boolean[descending.size()];
        int[] coll = new int[collations.size()];
        for (int i = 0; i < desc.length; i++) {
            desc[i] = descending.get(i);
            coll[i] = collations.get(i);
        }
        return new SortKeys(columns.toArray(new String[0]), desc, coll);
    }

    /** Orders the current row of {@code real} against {@code entry}; ties favour the real row. */
    int compare(Cursor real, int[] realColumns, MediaEntry entry) {
        for (int i = 0; i < columns.length; i++) {
            int order = compareValues(read(real, realColumns[i]), entry.value(columns[i]), collations[i]);
            if (order != 0) {
                return descending[i] ? -order : order;
            }
        }
        return 0;
    }

    private int compareValues(Object a, Object b, int collation) {
        int rankA = rank(a);
        int rankB = rank(b);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }
        if (a instanceof Number) {
            Number x = (Number) a;
            Number y = (Number) b;
            if (x instanceof Double || y instanceof Double) {
                return Double.compare(x.doubleValue(), y.doubleValue());
            }
            return Long.compare(x.longValue(), y.longValue());
        }
        if (a instanceof String) {
            String x = (String) a;
            String y = (String) b;
            switch (collation) {
                case NOCASE:
                    return x.compareToIgnoreCase(y);
                case LOCALIZED:
                    return collator.compare(x, y);
                default:
                    return x.compareTo(y);
            }
        }
        return 0;
    }

    private static int rank(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return 1;
        }
        return value instanceof String ? 2 : 3;
    }

    static Object read(Cursor cursor, int column) {
        switch (cursor.getType(column)) {
            case Cursor.FIELD_TYPE_INTEGER:
                return cursor.getLong(column);
            case Cursor.FIELD_TYPE_FLOAT:
                return cursor.getDouble(column);
            case Cursor.FIELD_TYPE_STRING:
                return cursor.getString(column);
            case Cursor.FIELD_TYPE_BLOB:
                return cursor.getBlob(column);
            default:
                return null;
        }
    }
}
