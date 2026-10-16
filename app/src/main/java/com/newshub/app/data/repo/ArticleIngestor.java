package com.newshub.app.data.repo;

import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.FingerprintRow;
import com.newshub.app.data.db.model.MemberRow;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.classify.Classification;
import com.newshub.app.domain.classify.TopicClassifier;
import com.newshub.app.domain.dedup.ArticleFingerprint;
import com.newshub.app.domain.dedup.DedupIndex;
import com.newshub.app.domain.dedup.SimilarityScorer;
import com.newshub.app.domain.dedup.StoryStats;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.text.HtmlText;
import com.newshub.app.domain.text.TextNormalizer;
import com.newshub.app.domain.text.UrlNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores new publications: skips items that are already saved, classifies them, attaches each
 * one to an existing story when the duplicate detector is confident, and keeps story statistics
 * up to date.
 */
public final class ArticleIngestor {

    /** Publications older than this are not considered when looking for the same story. */
    public static final long DEDUP_WINDOW_MILLIS = 72L * 3_600_000L;
    public static final int MAX_ITEMS_PER_FETCH = 100;
    private static final int MAX_INDEX_ROWS = 6000;

    private final NewsDatabase db;
    private final SimilarityScorer scorer = new SimilarityScorer();

    public ArticleIngestor(NewsDatabase db) {
        this.db = db;
    }

    public static final class Result {
        public int inserted;
        public int newStories;
        /** Stories that became reported by 2+ sources during this ingest. */
        public final List<Long> becameImportant = new ArrayList<>();
    }

    /** One refresh run shares a single duplicate index so items of different sources meet. */
    public Session newSession(long now) {
        DedupIndex index = new DedupIndex(scorer);
        for (FingerprintRow r : db.articles().fingerprintsSince(now - DEDUP_WINDOW_MILLIS, MAX_INDEX_ROWS)) {
            index.add(ArticleFingerprint.of(r.id, r.sourceId, r.url, r.title, r.summary, r.publishedAt), r.clusterId);
        }
        return new Session(index);
    }

    public final class Session {
        private final DedupIndex index;

        Session(DedupIndex index) {
            this.index = index;
        }

        public Result ingest(SourceEntity source, List<ParsedItem> items, TopicClassifier classifier, long now,
                             long retentionCutoff) {
            return db.runInTransaction(() -> ingestLocked(source, items, classifier, now, retentionCutoff));
        }

        private Result ingestLocked(SourceEntity source, List<ParsedItem> items, TopicClassifier classifier, long now,
                                    long retentionCutoff) {
            Result result = new Result();
            Map<String, ParsedItem> byHash = new LinkedHashMap<>();
            for (ParsedItem item : items) {
                if (byHash.size() >= MAX_ITEMS_PER_FETCH) break;
                if (item.title == null || item.title.trim().isEmpty()) continue;
                byHash.put(guidHash(source.id, item), item);
            }
            if (byHash.isEmpty()) return result;
            Set<String> existing = new HashSet<>(db.articles().existingHashes(source.id, new ArrayList<>(byHash.keySet())));

            Category sourceCategory = Category.fromKey(source.category, Category.WORLD);
            boolean demo = SourceType.DEMO.name().equals(source.type);
            Set<Long> touched = new LinkedHashSet<>();
            Map<Long, Integer> sourceCountBefore = new LinkedHashMap<>();

            for (Map.Entry<String, ParsedItem> e : byHash.entrySet()) {
                if (existing.contains(e.getKey())) continue;
                ParsedItem item = e.getValue();
                long published = item.publishedAt > 0 ? Math.min(item.publishedAt, now) : now;
                if (published < retentionCutoff) continue;

                ArticleEntity a = new ArticleEntity();
                a.sourceId = source.id;
                a.sourceName = source.name;
                a.guidHash = e.getKey();
                a.url = item.link == null ? "" : item.link;
                a.canonicalUrl = UrlNormalizer.canonical(a.url);
                a.title = item.title.trim();
                a.summary = HtmlText.snippet(item.summary == null ? "" : item.summary, HtmlText.SNIPPET_LIMIT);
                a.imageUrl = HtmlText.isUsableImageUrl(item.imageUrl) ? item.imageUrl : null;
                a.author = item.author;
                a.publishedAt = published;
                a.fetchedAt = now;
                a.dateEstimated = item.publishedAt <= 0;
                a.isDemo = demo;
                applyClassification(a, classifier.classify(a.title, a.summary, item.tags, sourceCategory));
                a.searchText = TextNormalizer.searchText(a.title, a.summary);

                ArticleFingerprint fp = ArticleFingerprint.of(0, source.id, a.url, a.title, a.summary, published);
                DedupIndex.Match match = index.findSameStory(fp);
                boolean createdCluster = false;
                long clusterId;
                ClusterEntity existingCluster = match == null ? null : db.clusters().get(match.clusterId);
                if (existingCluster != null) {
                    clusterId = existingCluster.id;
                    if (!sourceCountBefore.containsKey(clusterId)) {
                        sourceCountBefore.put(clusterId, existingCluster.sourceCount);
                    }
                } else {
                    ClusterEntity c = new ClusterEntity();
                    c.firstPublishedAt = published;
                    c.lastPublishedAt = published;
                    c.primaryCategory = a.primaryCategory;
                    clusterId = db.clusters().insert(c);
                    createdCluster = true;
                    sourceCountBefore.put(clusterId, 0);
                }
                a.clusterId = clusterId;
                long id = db.articles().insert(a);
                if (id <= 0) {
                    if (createdCluster) db.clusters().delete(clusterId);
                    continue;
                }
                index.add(ArticleFingerprint.of(id, source.id, a.url, a.title, a.summary, published), clusterId);
                touched.add(clusterId);
                result.inserted++;
                if (createdCluster) result.newStories++;
            }

            for (long clusterId : touched) {
                StoryStats stats = recomputeCluster(db, clusterId);
                Integer before = sourceCountBefore.get(clusterId);
                if (stats != null && stats.sourceCount >= 2 && (before == null || before < 2)) {
                    result.becameImportant.add(clusterId);
                }
            }
            return result;
        }
    }

    static void applyClassification(ArticleEntity a, Classification c) {
        a.categoryMask = c.categoryMask;
        a.primaryCategory = c.primary.key;
        a.politicsScore = c.politics.score;
        a.politicsMatches = c.politics.matches.isEmpty() ? null : String.join(", ", c.politics.matches);
        a.political = effectivePolitical(c.politics.political, a.politicsOverride);
    }

    static boolean effectivePolitical(boolean computed, int override) {
        if (override > 0) return true;
        if (override < 0) return false;
        return computed;
    }

    /** Re-aggregates a story; deletes it when it has no publications left. */
    public static StoryStats recomputeCluster(NewsDatabase db, long clusterId) {
        List<MemberRow> rows = db.articles().members(clusterId);
        if (rows.isEmpty()) {
            db.clusters().delete(clusterId);
            return null;
        }
        List<StoryStats.Member> members = new ArrayList<>(rows.size());
        for (MemberRow r : rows) {
            members.add(new StoryStats.Member(r.sourceId, r.publishedAt, r.categoryMask, r.primaryCategory,
                    r.politicsScore, r.political));
        }
        StoryStats s = StoryStats.aggregate(members);
        ClusterEntity c = db.clusters().get(clusterId);
        if (c == null) return s;
        c.articleCount = s.articleCount;
        c.sourceCount = s.sourceCount;
        c.firstPublishedAt = s.firstPublishedAt;
        c.lastPublishedAt = s.lastPublishedAt;
        c.categoryMask = s.categoryMask;
        c.primaryCategory = s.primaryCategory;
        c.political = s.political;
        c.politicsScore = s.politicsScore;
        db.clusters().update(c);
        return s;
    }

    static String guidHash(String sourceId, ParsedItem item) {
        String key = sourceId + "|" + (item.guid != null && !item.guid.isEmpty() ? item.guid
                : (item.link != null && !item.link.isEmpty() ? item.link : item.title));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }
}
