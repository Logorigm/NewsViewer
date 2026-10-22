package com.newshub.app.domain.feed;

public enum FeedMode {
    /** Everything, subject to the global politics filter. */
    ALL("all"),
    /** Political stories are always hidden in this mode. */
    NO_POLITICS("no_politics"),
    /** Ranked selection, see InterestRanker. */
    INTERESTING("interesting"),
    /** Single category. */
    CATEGORY("category"),
    /** Only the stories that the politics filter marks as political ("Скрытые публикации"). */
    POLITICAL_ONLY("political_only");

    public final String key;

    FeedMode(String key) {
        this.key = key;
    }

    public static FeedMode fromKey(String key, FeedMode fallback) {
        for (FeedMode m : values()) {
            if (m.key.equals(key)) return m;
        }
        return fallback;
    }
}
