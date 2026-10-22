package com.newshub.app.domain;

/** Kinds of sources. Every type is served by its own SourceProvider implementation. */
public enum SourceType {
    /** RSS 2.0 / RSS 1.0 (RDF) / Atom feed, or a website whose page advertises a feed. */
    RSS,
    /** Public Telegram channel read through the public web preview page t.me/s/&lt;channel&gt;. */
    TELEGRAM,
    /** Generated demo content. Exists only in debug builds and is always labelled as demo. */
    DEMO;

    public static SourceType parse(String value, SourceType fallback) {
        if (value == null) return fallback;
        for (SourceType t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        return fallback;
    }
}
