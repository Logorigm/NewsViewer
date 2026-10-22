package com.newshub.app.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Content categories. {@link #key} is the stable value stored in the database and in
 * sources.json; {@link #bit} is used for the category bitmask of articles and story clusters.
 *
 * <p>{@link #MAIN} is special: it is never assigned by the classifier. A story cluster gets the
 * MAIN bit when it is reported by two or more independent sources.
 */
public enum Category {
    MAIN("main", 0),
    TECH("tech", 1),
    AI("ai", 2),
    SCIENCE("science", 3),
    GAMES("games", 4),
    INTERNET("internet", 5),
    ENTERTAINMENT("entertainment", 6),
    WORLD("world", 7),
    INCIDENTS("incidents", 8),
    ECONOMY("economy", 9),
    SPORT("sport", 10),
    HEALTH("health", 11);

    public final String key;
    public final int bit;

    Category(String key, int bit) {
        this.key = key;
        this.bit = bit;
    }

    public int mask() {
        return 1 << bit;
    }

    public static Category fromKey(String key, Category fallback) {
        if (key == null) return fallback;
        for (Category c : values()) {
            if (c.key.equals(key)) return c;
        }
        return fallback;
    }

    public static int maskOf(Collection<Category> categories) {
        int mask = 0;
        for (Category c : categories) mask |= c.mask();
        return mask;
    }

    public static List<Category> fromMask(int mask) {
        List<Category> out = new ArrayList<>();
        for (Category c : values()) {
            if ((mask & c.mask()) != 0) out.add(c);
        }
        return out;
    }

    /** Categories that a source or an article can belong to (everything except MAIN). */
    public static List<Category> assignable() {
        List<Category> out = new ArrayList<>();
        for (Category c : values()) {
            if (c != MAIN) out.add(c);
        }
        return Collections.unmodifiableList(out);
    }
}
