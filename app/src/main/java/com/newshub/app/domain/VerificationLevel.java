package com.newshub.app.domain;

/**
 * How well a built-in source address was verified when the source list was compiled.
 * The app also checks every source at runtime and shows the real status in the Sources tab.
 */
public enum VerificationLevel {
    /** Address confirmed by the publisher or by an independent feed directory. */
    CONFIRMED,
    /** Commonly used, documented-by-practice address that was not re-checked at compile time. */
    KNOWN,
    /** Only the website is known; the feed address is auto-discovered from the page at runtime. */
    DISCOVERY,
    /** No reliable address found. The source is disabled until the user fills in the address. */
    UNVERIFIED;

    public static VerificationLevel parse(String value) {
        if (value == null) return UNVERIFIED;
        for (VerificationLevel v : values()) {
            if (v.name().equalsIgnoreCase(value)) return v;
        }
        return UNVERIFIED;
    }
}
