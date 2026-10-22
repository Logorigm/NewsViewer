package com.newshub.app.domain.feed;

public enum SortOrder {
    /** Newest stories first (by the time of the first report). */
    NEWEST("newest"),
    /** Stories reported by more independent sources first, then newest. */
    MOST_SOURCES("sources");

    public final String key;

    SortOrder(String key) {
        this.key = key;
    }

    public static SortOrder fromKey(String key, SortOrder fallback) {
        for (SortOrder s : values()) {
            if (s.key.equals(key)) return s;
        }
        return fallback;
    }
}
