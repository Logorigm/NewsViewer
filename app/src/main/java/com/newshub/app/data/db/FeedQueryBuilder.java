package com.newshub.app.data.db;

import com.newshub.app.domain.Category;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.SortOrder;
import com.newshub.app.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the SQL for feed screens from a {@link FeedFilter}. Filtering happens in the query, so
 * nothing is ever deleted because it does not match the current filter.
 */
public final class FeedQueryBuilder {

    private FeedQueryBuilder() {
    }

    /** SQL text plus bind arguments (kept free of Android types so it can be unit tested). */
    public static final class Built {
        public final String sql;
        public final Object[] args;

        Built(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args.toArray();
        }
    }

    private static final String GROUPED_COLUMNS = "c.id AS clusterId, c.sourceCount AS sourceCount, "
            + "c.articleCount AS articleCount, c.firstPublishedAt AS firstPublishedAt, "
            + "c.lastPublishedAt AS lastPublishedAt, c.categoryMask AS categoryMask, "
            + "c.primaryCategory AS primaryCategory, c.political AS political, c.politicsScore AS politicsScore, "
            + "a.id AS articleId, a.title AS title, a.summary AS summary, a.imageUrl AS imageUrl, a.url AS url, "
            + "a.publishedAt AS publishedAt, a.sourceId AS sourceId, a.sourceName AS sourceName, "
            + "a.politicsMatches AS politicsMatches, a.isDemo AS isDemo, "
            + "EXISTS(SELECT 1 FROM favorites f WHERE f.articleId = a.id) AS isFavorite, "
            + "EXISTS(SELECT 1 FROM history h JOIN articles hx ON hx.id = h.articleId WHERE hx.clusterId = c.id) AS isRead";

    private static final String FLAT_COLUMNS = "a.clusterId AS clusterId, COALESCE(c.sourceCount, 1) AS sourceCount, "
            + "COALESCE(c.articleCount, 1) AS articleCount, a.publishedAt AS firstPublishedAt, "
            + "a.publishedAt AS lastPublishedAt, "
            + "(a.categoryMask | (COALESCE(c.categoryMask, 0) & " + Category.MAIN.mask() + ")) AS categoryMask, "
            + "a.primaryCategory AS primaryCategory, a.political AS political, a.politicsScore AS politicsScore, "
            + "a.id AS articleId, a.title AS title, a.summary AS summary, a.imageUrl AS imageUrl, a.url AS url, "
            + "a.publishedAt AS publishedAt, a.sourceId AS sourceId, a.sourceName AS sourceName, "
            + "a.politicsMatches AS politicsMatches, a.isDemo AS isDemo, "
            + "EXISTS(SELECT 1 FROM favorites f WHERE f.articleId = a.id) AS isFavorite, "
            + "EXISTS(SELECT 1 FROM history h WHERE h.articleId = a.id) AS isRead";

    /** Rows of a feed. {@code limit} overrides the filter's limit when positive. */
    public static Built rows(FeedFilter f, long now, int limitOverride) {
        int limit = limitOverride > 0 ? limitOverride : f.limit;
        return f.groupDuplicates ? grouped(f, now, limit, false) : flat(f, now, limit, false);
    }

    /** Number of political stories hidden by the filter (same scope, politics only). */
    public static Built politicalCount(FeedFilter f, long now) {
        FeedFilter politicalOnly = f.withMode(FeedMode.POLITICAL_ONLY, f.mode == FeedMode.CATEGORY ? f.category : null);
        return f.groupDuplicates ? grouped(politicalOnly, now, 0, true) : flat(politicalOnly, now, 0, true);
    }

    private static Built grouped(FeedFilter f, long now, int limit, boolean count) {
        List<Object> args = new ArrayList<>();
        StringBuilder representative = new StringBuilder(
                "SELECT x.id FROM articles x JOIN sources sx ON sx.id = x.sourceId "
                        + "WHERE x.clusterId = c.id AND sx.enabled = 1 AND sx.deleted = 0");
        List<Object> repArgs = new ArrayList<>();
        if (f.sourceId != null) {
            representative.append(" AND x.sourceId = ?");
            repArgs.add(f.sourceId);
        }
        StringBuilder sql = new StringBuilder();
        if (count) {
            sql.append("SELECT COUNT(*) FROM clusters c WHERE EXISTS (").append(representative).append(")");
            args.addAll(repArgs);
        } else {
            representative.append(" ORDER BY (x.imageUrl IS NOT NULL AND x.imageUrl <> '') DESC, x.publishedAt ASC LIMIT 1");
            sql.append("SELECT ").append(GROUPED_COLUMNS)
                    .append(" FROM clusters c JOIN articles a ON a.id = (").append(representative).append(")")
                    .append(" WHERE 1 = 1");
            args.addAll(repArgs);
        }
        sql.append(" AND c.firstPublishedAt >= ?");
        args.add(f.period.since(now));
        appendCategory(sql, args, f, "c.categoryMask");
        appendPolitics(sql, f, "c.political", "c.sourceCount");
        List<String> terms = f.query == null ? new ArrayList<>() : TextNormalizer.searchTerms(f.query);
        if (!terms.isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM articles q WHERE q.clusterId = c.id");
            for (String t : terms) {
                sql.append(" AND q.searchText LIKE ?");
                args.add("%" + t + "%");
            }
            sql.append(")");
        }
        if (!count) {
            if (f.sort == SortOrder.MOST_SOURCES) {
                sql.append(" ORDER BY c.sourceCount DESC, c.firstPublishedAt DESC");
            } else {
                sql.append(" ORDER BY c.firstPublishedAt DESC");
            }
            sql.append(" LIMIT ?");
            args.add(limit);
        }
        return new Built(sql.toString(), args);
    }

    private static Built flat(FeedFilter f, long now, int limit, boolean count) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(count ? "SELECT COUNT(*)" : "SELECT " + FLAT_COLUMNS);
        sql.append(" FROM articles a JOIN sources s ON s.id = a.sourceId AND s.enabled = 1 AND s.deleted = 0")
                .append(" LEFT JOIN clusters c ON c.id = a.clusterId WHERE a.publishedAt >= ?");
        args.add(f.period.since(now));
        if (f.sourceId != null) {
            sql.append(" AND a.sourceId = ?");
            args.add(f.sourceId);
        }
        appendCategory(sql, args, f,
                "(a.categoryMask | (COALESCE(c.categoryMask, 0) & " + Category.MAIN.mask() + "))");
        appendPolitics(sql, f, "a.political", "COALESCE(c.sourceCount, 1)");
        if (f.query != null) {
            for (String t : TextNormalizer.searchTerms(f.query)) {
                sql.append(" AND a.searchText LIKE ?");
                args.add("%" + t + "%");
            }
        }
        if (!count) {
            if (f.sort == SortOrder.MOST_SOURCES) {
                sql.append(" ORDER BY COALESCE(c.sourceCount, 1) DESC, a.publishedAt DESC");
            } else {
                sql.append(" ORDER BY a.publishedAt DESC");
            }
            sql.append(" LIMIT ?");
            args.add(limit);
        }
        return new Built(sql.toString(), args);
    }

    private static void appendCategory(StringBuilder sql, List<Object> args, FeedFilter f, String maskExpr) {
        if (f.category != null && (f.mode == FeedMode.CATEGORY || f.mode == FeedMode.POLITICAL_ONLY)) {
            sql.append(" AND (").append(maskExpr).append(" & ?) <> 0");
            args.add(f.category.mask());
        }
    }

    private static void appendPolitics(StringBuilder sql, FeedFilter f, String politicalExpr, String sourceCountExpr) {
        if (f.mode == FeedMode.POLITICAL_ONLY) {
            sql.append(" AND ").append(politicalExpr).append(" = 1");
        } else if (f.hidesPolitics()) {
            sql.append(" AND ").append(politicalExpr).append(" = 0");
        } else if (f.limitsPolitics()) {
            sql.append(" AND (").append(politicalExpr).append(" = 0 OR ").append(sourceCountExpr).append(" >= 2)");
        }
    }
}
