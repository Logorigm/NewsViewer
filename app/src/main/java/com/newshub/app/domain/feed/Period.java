package com.newshub.app.domain.feed;

/** Freshness window of the feed. */
public enum Period {
    DAY("day", 1),
    WEEK("week", 7),
    MONTH("month", 30),
    YEAR("year", 365),
    ALL("all", -1);

    public final String key;
    public final int days;

    Period(String key, int days) {
        this.key = key;
        this.days = days;
    }

    /** Start of the window in epoch millis, or 0 for {@link #ALL}. */
    public long since(long now) {
        return days < 0 ? 0 : now - days * 86_400_000L;
    }

    public static Period fromKey(String key, Period fallback) {
        for (Period p : values()) {
            if (p.key.equals(key)) return p;
        }
        return fallback;
    }
}
