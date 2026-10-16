package com.newshub.app.domain.dedup;

/** Result of comparing two publications. */
public final class DedupDecision {

    public enum Verdict {
        /** Same event with high confidence: merge into one story. */
        SAME,
        /** Similar, but confidence is too low to merge automatically. */
        UNCERTAIN,
        /** Different events. */
        DIFFERENT
    }

    public final Verdict verdict;
    public final double score;
    public final String reason;

    public DedupDecision(Verdict verdict, double score, String reason) {
        this.verdict = verdict;
        this.score = score;
        this.reason = reason;
    }

    @Override
    public String toString() {
        return verdict + " (" + String.format(java.util.Locale.ROOT, "%.2f", score) + "): " + reason;
    }
}
