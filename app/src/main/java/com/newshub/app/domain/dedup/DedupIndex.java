package com.newshub.app.domain.dedup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory index of recent publications (with their story cluster ids) that finds the best
 * existing story for a new publication. An inverted index on headline stems keeps the number of
 * pairwise comparisons small.
 */
public final class DedupIndex {

    /** Stems that occur in very many headlines are useless for candidate lookup. */
    private static final int MAX_POSTING = 400;

    public static final class Match {
        public final long clusterId;
        public final long articleId;
        public final DedupDecision decision;

        Match(long clusterId, long articleId, DedupDecision decision) {
            this.clusterId = clusterId;
            this.articleId = articleId;
            this.decision = decision;
        }
    }

    private static final class Entry {
        final ArticleFingerprint fp;
        final long clusterId;

        Entry(ArticleFingerprint fp, long clusterId) {
            this.fp = fp;
            this.clusterId = clusterId;
        }
    }

    private final SimilarityScorer scorer;
    private final Map<String, List<Entry>> byStem = new HashMap<>();
    private final Map<String, Entry> byUrl = new HashMap<>();
    private int size;

    public DedupIndex(SimilarityScorer scorer) {
        this.scorer = scorer;
    }

    public int size() {
        return size;
    }

    public void add(ArticleFingerprint fp, long clusterId) {
        Entry e = new Entry(fp, clusterId);
        for (String stem : fp.titleStems) {
            byStem.computeIfAbsent(stem, k -> new ArrayList<>()).add(e);
        }
        if (!fp.canonicalUrl.isEmpty()) byUrl.put(fp.canonicalUrl, e);
        size++;
    }

    /** @return the best SAME match, or null when the publication starts a new story. */
    public Match findSameStory(ArticleFingerprint fp) {
        Entry byLink = fp.canonicalUrl.isEmpty() ? null : byUrl.get(fp.canonicalUrl);
        if (byLink != null) {
            return new Match(byLink.clusterId, byLink.fp.id, scorer.compare(fp, byLink.fp));
        }
        Map<Entry, Integer> shared = new HashMap<>();
        for (String stem : fp.titleStems) {
            List<Entry> posting = byStem.get(stem);
            if (posting == null || posting.size() > MAX_POSTING) continue;
            for (Entry e : posting) shared.merge(e, 1, Integer::sum);
        }
        Match best = null;
        for (Map.Entry<Entry, Integer> c : shared.entrySet()) {
            if (c.getValue() < 2) continue;
            Entry e = c.getKey();
            DedupDecision d = scorer.compare(fp, e.fp);
            if (d.verdict != DedupDecision.Verdict.SAME) continue;
            if (best == null || d.score > best.decision.score) {
                best = new Match(e.clusterId, e.fp.id, d);
            }
        }
        return best;
    }

    /** Publications that look similar but were not merged (shown as "возможно, о том же"). */
    public List<Match> findUncertain(ArticleFingerprint fp, int limit) {
        List<Match> out = new ArrayList<>();
        Map<Entry, Integer> shared = new HashMap<>();
        for (String stem : fp.titleStems) {
            List<Entry> posting = byStem.get(stem);
            if (posting == null || posting.size() > MAX_POSTING) continue;
            for (Entry e : posting) shared.merge(e, 1, Integer::sum);
        }
        for (Map.Entry<Entry, Integer> c : shared.entrySet()) {
            if (c.getValue() < 2 || c.getKey().fp.id == fp.id) continue;
            DedupDecision d = scorer.compare(fp, c.getKey().fp);
            if (d.verdict == DedupDecision.Verdict.UNCERTAIN) {
                out.add(new Match(c.getKey().clusterId, c.getKey().fp.id, d));
            }
        }
        out.sort((x, y) -> Double.compare(y.decision.score, x.decision.score));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
