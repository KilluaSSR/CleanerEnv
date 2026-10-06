package killua.dev.cleaner.guard;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.text.Collator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selection, order and paging of a MediaStore query, in whichever form the app passed them. */
final class QueryShape {
    private static final Pattern GROUP_BY = Pattern.compile("(?i)\\bgroup\\s+by\\b");
    private static final Pattern SORT_LIMIT = Pattern.compile(
            "(?is)^(.*?)\\s+LIMIT\\s+(\\d+)(?:\\s*,\\s*(\\d+)|\\s+OFFSET\\s+(\\d+))?\\s*$");
    private static final Pattern SQL_LIMIT = Pattern.compile(
            "(?is)^\\s*(-?\\d+)(?:\\s*,\\s*(\\d+)|\\s+OFFSET\\s+(\\d+))?\\s*$");
    private static final String QUERY_ARG_SQL_HAVING = "android:query-arg-sql-having";

    private enum LimitSource { NONE, ARGS, SQL, SORT, URI }

    final String selection;
    final String[] selectionArgs;
    /** Sort order without any LIMIT clause. */
    final String sort;
    /** Rows to return, {@code < 0} for all. */
    final long limit;
    final long offset;
    private final LimitSource source;

    private QueryShape(String selection, String[] selectionArgs, String sort, long limit, long offset,
                       LimitSource source) {
        this.selection = selection;
        this.selectionArgs = selectionArgs;
        this.sort = sort;
        this.limit = limit;
        this.offset = offset;
        this.source = source;
    }

    /** {@code null} for grouped or otherwise unmergeable queries. */
    static QueryShape parse(Uri uri, Bundle args) {
        Bundle in = args == null ? Bundle.EMPTY : args;
        if (in.containsKey(ContentResolver.QUERY_ARG_SQL_GROUP_BY)
                || in.containsKey(ContentResolver.QUERY_ARG_GROUP_COLUMNS)
                || in.containsKey(QUERY_ARG_SQL_HAVING)) {
            return null;
        }
        String selection = in.getString(ContentResolver.QUERY_ARG_SQL_SELECTION);
        if (selection != null && GROUP_BY.matcher(selection).find()) {
            return null;
        }
        String[] selectionArgs = in.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS);
        String sort = in.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER);
        if (sort == null) {
            sort = structuredSort(in);
        }

        if (in.containsKey(ContentResolver.QUERY_ARG_LIMIT) || in.containsKey(ContentResolver.QUERY_ARG_OFFSET)) {
            return new QueryShape(selection, selectionArgs, sort,
                    in.getInt(ContentResolver.QUERY_ARG_LIMIT, -1),
                    Math.max(in.getInt(ContentResolver.QUERY_ARG_OFFSET, 0), 0), LimitSource.ARGS);
        }
        String sqlLimit = in.getString(ContentResolver.QUERY_ARG_SQL_LIMIT);
        if (sqlLimit != null) {
            long[] window = limitClause(sqlLimit);
            return window == null ? null
                    : new QueryShape(selection, selectionArgs, sort, window[0], window[1], LimitSource.SQL);
        }
        if (sort != null) {
            Matcher matcher = SORT_LIMIT.matcher(sort);
            if (matcher.matches()) {
                long first = Long.parseLong(matcher.group(2));
                long limit = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : first;
                long offset = matcher.group(3) != null ? first
                        : matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;
                return new QueryShape(selection, selectionArgs, matcher.group(1), limit, offset, LimitSource.SORT);
            }
        }
        String uriLimit = uri.getQueryParameter("limit");
        if (uriLimit != null) {
            long[] window = limitClause(uriLimit);
            // The URI keeps its limit on the real query, which only works from the first row.
            return window == null || window[1] != 0 ? null
                    : new QueryShape(selection, selectionArgs, sort, window[0], 0, LimitSource.URI);
        }
        return new QueryShape(selection, selectionArgs, sort, -1, 0, LimitSource.NONE);
    }

    /** Rows the merge needs from each side: everything up to the end of the requested page. */
    long window() {
        return limit < 0 ? -1 : offset + limit;
    }

    /** Arguments for the real query: same order, paging widened to start at the first row. */
    Bundle rewrite(Bundle real) {
        Bundle out = real == null ? new Bundle() : new Bundle(real);
        long window = window();
        String count = String.valueOf(window);
        switch (source) {
            case ARGS:
                out.remove(ContentResolver.QUERY_ARG_OFFSET);
                if (window >= 0) {
                    out.putInt(ContentResolver.QUERY_ARG_LIMIT, (int) Math.min(window, Integer.MAX_VALUE));
                } else {
                    out.remove(ContentResolver.QUERY_ARG_LIMIT);
                }
                break;
            case SQL:
                if (window >= 0) {
                    out.putString(ContentResolver.QUERY_ARG_SQL_LIMIT, count);
                } else {
                    out.remove(ContentResolver.QUERY_ARG_SQL_LIMIT);
                }
                break;
            case SORT:
                out.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        window >= 0 ? sort + " LIMIT " + count : sort);
                break;
            default:
                break;
        }
        return out;
    }

    /** {limit, offset} of a SQL limit clause; a negative limit means none. */
    private static long[] limitClause(String clause) {
        Matcher matcher = SQL_LIMIT.matcher(clause);
        if (!matcher.matches()) {
            return null;
        }
        long first = Long.parseLong(matcher.group(1));
        if (matcher.group(2) != null) {
            return new long[]{Long.parseLong(matcher.group(2)), Math.max(first, 0)};
        }
        long offset = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        return new long[]{first, offset};
    }

    /** Same clause {@code ContentResolver.createSqlSortClause} builds. */
    private static String structuredSort(Bundle in) {
        String[] columns = in.getStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS);
        if (columns == null || columns.length == 0) {
            return null;
        }
        String clause = TextUtils.join(", ", columns);
        int collation = in.getInt(ContentResolver.QUERY_ARG_SORT_COLLATION, Collator.IDENTICAL);
        if (collation == Collator.PRIMARY || collation == Collator.SECONDARY) {
            clause += " COLLATE NOCASE";
        }
        int direction = in.getInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, Integer.MIN_VALUE);
        if (direction == ContentResolver.QUERY_SORT_DIRECTION_ASCENDING) {
            clause += " ASC";
        } else if (direction == ContentResolver.QUERY_SORT_DIRECTION_DESCENDING) {
            clause += " DESC";
        }
        return clause;
    }
}
