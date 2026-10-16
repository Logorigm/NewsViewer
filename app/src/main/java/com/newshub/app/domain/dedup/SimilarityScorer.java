package com.newshub.app.domain.dedup;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether two publications report the same event.
 *
 * <p>Signals: identical canonical link; overlap of headline word stems (Jaccard and containment);
 * overlap of word pairs in the excerpt; overlap of named entities; publication time. Guards
 * against merging different events with similar headlines: conflicting numbers ("курс 95" vs
 * "курс 97"), too few shared words, and a large time gap. Anything below the merge threshold but
 * still similar is reported as UNCERTAIN and is not merged.
 */
public final class SimilarityScorer {

    public static final double MERGE_THRESHOLD = 0.55;
    public static final double UNCERTAIN_THRESHOLD = 0.38;
    /** Stories further apart than this are never merged. */
    public static final long MAX_GAP_MILLIS = 48L * 3_600_000L;
    private static final long SOFT_GAP_MILLIS = 24L * 3_600_000L;
    /** Stricter threshold for two items of the same source (updates/reposts of one story). */
    private static final double SAME_SOURCE_THRESHOLD = 0.75;

    public DedupDecision compare(ArticleFingerprint a, ArticleFingerprint b) {
        if (!a.canonicalUrl.isEmpty() && a.canonicalUrl.equals(b.canonicalUrl)) {
            return new DedupDecision(DedupDecision.Verdict.SAME, 1.0, "одинаковая ссылка");
        }
        long gap = Math.abs(a.publishedAt - b.publishedAt);
        if (gap > MAX_GAP_MILLIS) {
            return new DedupDecision(DedupDecision.Verdict.DIFFERENT, 0, "большой разрыв во времени");
        }
        if (!a.numbers.isEmpty() && !b.numbers.isEmpty() && !intersects(a.numbers, b.numbers)) {
            return new DedupDecision(DedupDecision.Verdict.DIFFERENT, 0, "в заголовках разные числа");
        }

        int shared = intersectionSize(a.titleStems, b.titleStems);
        int minSize = Math.min(a.titleStems.size(), b.titleStems.size());
        double jaccard = jaccard(a.titleStems, b.titleStems);
        double containment = minSize == 0 ? 0 : (double) shared / minSize;
        double titleScore = minSize >= 3 ? Math.max(jaccard, 0.85 * containment) : jaccard;

        // Excerpt similarity only adds confidence: two reports of one event are usually worded
        // differently, so a low value must not count against merging.
        double textScore = (a.textShingles.isEmpty() || b.textShingles.isEmpty())
                ? 0 : jaccard(a.textShingles, b.textShingles);
        double entityScore;
        boolean entityConflict = false;
        if (a.entities.isEmpty() && b.entities.isEmpty()) {
            entityScore = 0.5;
        } else if (a.entities.isEmpty() || b.entities.isEmpty()) {
            entityScore = 0.3;
        } else {
            entityScore = intersects(a.entities, b.entities) ? Math.max(0.6, jaccard(a.entities, b.entities)) : 0;
            // Each headline names something the other one does not ("выставка Айвазовского" vs
            // "выставка Шагала"): likely different events with a shared template.
            entityConflict = !b.entities.containsAll(a.entities) && !a.entities.containsAll(b.entities);
        }

        double score = (0.65 * titleScore + 0.15 * entityScore) / 0.80 + 0.25 * textScore;
        if (entityConflict) score -= 0.15;
        if (gap > SOFT_GAP_MILLIS) score -= 0.08;
        score = Math.max(0, Math.min(1, score));

        int requiredShared = minSize <= 4 ? 2 : 3;
        boolean sameSource = a.sourceId != null && a.sourceId.equals(b.sourceId);
        double threshold = sameSource ? SAME_SOURCE_THRESHOLD : MERGE_THRESHOLD;

        if (shared >= requiredShared && score >= threshold) {
            return new DedupDecision(DedupDecision.Verdict.SAME, score,
                    "совпадают слова заголовка (" + shared + ")");
        }
        if (score >= UNCERTAIN_THRESHOLD && shared >= 2) {
            return new DedupDecision(DedupDecision.Verdict.UNCERTAIN, score, "похоже, но уверенность низкая");
        }
        return new DedupDecision(DedupDecision.Verdict.DIFFERENT, score, "разные события");
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        int inter = intersectionSize(a, b);
        int union = a.size() + b.size() - inter;
        return union == 0 ? 0 : (double) inter / union;
    }

    static int intersectionSize(Set<String> a, Set<String> b) {
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = small == a ? b : a;
        int n = 0;
        for (String s : small) {
            if (large.contains(s)) n++;
        }
        return n;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        Set<String> copy = new HashSet<>(a);
        copy.retainAll(b);
        return !copy.isEmpty();
    }
}
