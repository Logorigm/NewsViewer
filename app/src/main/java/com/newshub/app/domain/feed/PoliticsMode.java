package com.newshub.app.domain.feed;

/** What the politics filter does when it is switched on. */
public enum PoliticsMode {
    /** Political stories are hidden from the feeds (still stored and viewable separately). */
    HIDE("hide"),
    /**
     * Only widely reported political stories (2+ independent sources) are shown, and at most
     * {@link PoliticsLimiter#DEFAULT_SHARE} of the feed may be political.
     */
    LIMIT("limit");

    public final String key;

    PoliticsMode(String key) {
        this.key = key;
    }

    public static PoliticsMode fromKey(String key, PoliticsMode fallback) {
        for (PoliticsMode m : values()) {
            if (m.key.equals(key)) return m;
        }
        return fallback;
    }
}
