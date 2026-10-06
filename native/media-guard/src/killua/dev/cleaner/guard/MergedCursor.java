package killua.dev.cleaner.guard;

import android.content.ContentResolver;
import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import java.util.List;

/**
 * Real MediaStore rows interleaved with an app's isolated rows. Real rows are read through
 * in place; {@code order[i] >= 0} is a real position, otherwise {@code -(isolated index + 1)}.
 */
final class MergedCursor extends AbstractCursor {
    private final Cursor real;
    private final String[] columns;
    private final int[] order;
    private final Object[][] isolated;

    private MergedCursor(Cursor real, String[] columns, int[] order, Object[][] isolated) {
        this.real = real;
        this.columns = columns;
        this.order = order;
        this.isolated = isolated;
    }

    /**
     * @param projection the app's projection; the real cursor may carry extra trailing columns
     *                   that were only added to compare sort keys
     * @param entries    isolated rows, already in the app's order and cut to the page window
     */
    static MergedCursor merge(Cursor real, String[] projection, List<MediaEntry> entries,
                              SortKeys keys, long offset, long limit) {
        String[] columns = projection != null ? projection.clone()
                : real != null ? real.getColumnNames() : MediaEntry.COLUMNS.clone();
        Object[][] isolated = new Object[entries.size()][];
        for (int i = 0; i < isolated.length; i++) {
            MediaEntry entry = entries.get(i);
            Object[] row = new Object[columns.length];
            for (int c = 0; c < columns.length; c++) {
                row[c] = entry.value(columns[c]);
            }
            isolated[i] = row;
        }

        int[] keyColumns = null;
        if (keys != null && real != null) {
            keyColumns = new int[keys.columns.length];
            for (int i = 0; i < keyColumns.length; i++) {
                keyColumns[i] = real.getColumnIndex(keys.columns[i]);
                if (keyColumns[i] < 0) {
                    keyColumns = null;
                    break;
                }
            }
        }

        int realCount = real == null ? 0 : real.getCount();
        long window = limit < 0 ? Long.MAX_VALUE : offset + limit;
        int total = (int) Math.min((long) realCount + isolated.length, window);
        int[] merged = new int[total];
        int r = 0;
        int s = 0;
        for (int i = 0; i < total; i++) {
            boolean takeReal;
            if (r >= realCount) {
                takeReal = false;
            } else if (s >= isolated.length || keyColumns == null) {
                takeReal = true;
            } else {
                real.moveToPosition(r);
                takeReal = keys.compare(real, keyColumns, entries.get(s)) <= 0;
            }
            merged[i] = takeReal ? r++ : -(++s);
        }
        int skip = (int) Math.min(offset, total);
        int[] order = new int[total - skip];
        System.arraycopy(merged, skip, order, 0, order.length);
        return new MergedCursor(real, columns, order, isolated);
    }

    @Override
    public int getCount() {
        return order.length;
    }

    @Override
    public String[] getColumnNames() {
        return columns;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        int at = order[newPosition];
        return at < 0 || real.moveToPosition(at);
    }

    private Object isolatedValue(int column) {
        int at = order[getPosition()];
        return at < 0 ? isolated[-at - 1][column] : null;
    }

    private boolean onReal() {
        return order[getPosition()] >= 0;
    }

    @Override
    public String getString(int column) {
        if (onReal()) {
            return real.getString(column);
        }
        Object value = isolatedValue(column);
        return value == null ? null : value.toString();
    }

    @Override
    public short getShort(int column) {
        return onReal() ? real.getShort(column) : (short) number(column);
    }

    @Override
    public int getInt(int column) {
        return onReal() ? real.getInt(column) : (int) number(column);
    }

    @Override
    public long getLong(int column) {
        return onReal() ? real.getLong(column) : number(column);
    }

    @Override
    public float getFloat(int column) {
        return onReal() ? real.getFloat(column) : number(column);
    }

    @Override
    public double getDouble(int column) {
        return onReal() ? real.getDouble(column) : number(column);
    }

    @Override
    public byte[] getBlob(int column) {
        return onReal() ? real.getBlob(column) : null;
    }

    @Override
    public boolean isNull(int column) {
        return onReal() ? real.isNull(column) : isolatedValue(column) == null;
    }

    @Override
    public int getType(int column) {
        if (onReal()) {
            return real.getType(column);
        }
        Object value = isolatedValue(column);
        if (value == null) {
            return FIELD_TYPE_NULL;
        }
        return value instanceof Number ? FIELD_TYPE_INTEGER : FIELD_TYPE_STRING;
    }

    private long number(int column) {
        Object value = isolatedValue(column);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException notNumber) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public Bundle getExtras() {
        return real != null ? real.getExtras() : Bundle.EMPTY;
    }

    @Override
    public Uri getNotificationUri() {
        return real != null ? real.getNotificationUri() : super.getNotificationUri();
    }

    @Override
    public List<Uri> getNotificationUris() {
        return real != null ? real.getNotificationUris() : super.getNotificationUris();
    }

    @Override
    public void setNotificationUri(ContentResolver resolver, Uri uri) {
        if (real != null) {
            real.setNotificationUri(resolver, uri);
        } else {
            super.setNotificationUri(resolver, uri);
        }
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        if (real != null) {
            real.registerContentObserver(observer);
        } else {
            super.registerContentObserver(observer);
        }
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        if (real != null) {
            real.unregisterContentObserver(observer);
        } else {
            super.unregisterContentObserver(observer);
        }
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        super.registerDataSetObserver(observer);
        if (real != null) {
            real.registerDataSetObserver(observer);
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        super.unregisterDataSetObserver(observer);
        if (real != null) {
            real.unregisterDataSetObserver(observer);
        }
    }

    @Override
    public void close() {
        super.close();
        if (real != null) {
            real.close();
        }
    }
}
