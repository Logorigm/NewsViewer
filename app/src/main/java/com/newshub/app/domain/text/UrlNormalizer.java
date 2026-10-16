package com.newshub.app.domain.text;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Canonical form of article links, used to detect the same publication behind different URLs. */
public final class UrlNormalizer {

    private UrlNormalizer() {
    }

    private static final String[] TRACKING_PREFIXES = {
            "utm_", "fbclid", "gclid", "yclid", "ysclid", "from", "ref", "rss", "at_", "mc_", "_openstat",
            "share", "source", "erid"
    };

    public static String canonical(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "";
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null) return trimmed.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            for (String prefix : new String[]{"www.", "m.", "amp.", "mobile."}) {
                if (host.startsWith(prefix)) host = host.substring(prefix.length());
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.endsWith("/amp")) path = path.substring(0, path.length() - 4);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            String query = filterQuery(uri.getRawQuery());
            return host + path + (query.isEmpty() ? "" : "?" + query);
        } catch (Exception e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    private static String filterQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> kept = new ArrayList<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) continue;
            String name = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
            boolean tracking = false;
            for (String p : TRACKING_PREFIXES) {
                if (name.startsWith(p)) {
                    tracking = true;
                    break;
                }
            }
            if (!tracking) kept.add(part);
        }
        Collections.sort(kept);
        return String.join("&", kept);
    }

    /** Resolves a possibly relative link against a base URL; returns the input on failure. */
    public static String resolve(String base, String href) {
        if (href == null) return "";
        String h = href.trim();
        if (h.isEmpty()) return "";
        try {
            if (base == null || base.isEmpty()) return h;
            return new URI(base).resolve(h).toString();
        } catch (Exception e) {
            return h;
        }
    }

    /**
     * Normalizes a user-typed website or feed address: adds https:// when the scheme is missing
     * and upgrades http:// to https:// (cleartext traffic is disabled in the app).
     */
    public static String normalizeUserUrl(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) return "https://" + s.substring(7);
        if (!lower.startsWith("https://")) return "https://" + s;
        return s;
    }

    public static boolean isHttpsUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().contains(".");
        } catch (Exception e) {
            return false;
        }
    }
}
