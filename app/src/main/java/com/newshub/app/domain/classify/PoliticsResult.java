package com.newshub.app.domain.classify;

import java.util.Collections;
import java.util.List;

/** Outcome of the politics rules for one publication, including the reasons. */
public final class PoliticsResult {
    public final int score;
    public final boolean political;
    /** Matched rule words, in the order they were found (shown to the user as the reason). */
    public final List<String> matches;
    /** User exception word that cancelled the politics label, or null. */
    public final String exception;

    public PoliticsResult(int score, boolean political, List<String> matches, String exception) {
        this.score = score;
        this.political = political;
        this.matches = Collections.unmodifiableList(matches);
        this.exception = exception;
    }
}
