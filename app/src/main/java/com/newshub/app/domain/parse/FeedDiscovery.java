package com.newshub.app.domain.parse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds feed addresses advertised by a website. This lets a source be configured with just the
 * site address, so no feed URL has to be guessed: the page itself says where its feed is.
 */
public final class FeedDiscovery {

    private FeedDiscovery() {
    }

    private static final int MAX_CANDIDATES = 5;
    private static final Pattern FEED_TYPE = Pattern.compile(
            "application/(rss|atom|rdf)\\+xml|application/xml|text/xml", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEED_HREF = Pattern.compile(
            "(?i)(/rss/?$|/feed/?$|/atom/?$|\\.rss$|rss\\.xml$|feed\\.xml$|atom\\.xml$|/rss/[^/]+\\.xml$|/rss/all)");

    public static List<String> discover(byte[] html, String charset, String baseUrl) {
        try {
            Document doc = Jsoup.parse(new ByteArrayInputStream(html), charset, baseUrl == null ? "" : baseUrl);
            return discover(doc, baseUrl);
        } catch (IOException | RuntimeException e) {
            return new ArrayList<>();
        }
    }

    public static List<String> discover(String html, String baseUrl) {
        return discover(Jsoup.parse(html == null ? "" : html, baseUrl == null ? "" : baseUrl), baseUrl);
    }

    private static List<String> discover(Document doc, String baseUrl) {
        Set<String> out = new LinkedHashSet<>();
        for (Element link : doc.select("link[href]")) {
            String rel = link.attr("rel").toLowerCase(Locale.ROOT);
            String type = link.attr("type");
            if (rel.contains("alternate") && FEED_TYPE.matcher(type).find()) {
                String href = link.absUrl("href");
                if (!href.isEmpty()) out.add(href);
            }
            if (out.size() >= MAX_CANDIDATES) break;
        }
        if (out.isEmpty()) {
            String host = hostOf(baseUrl);
            for (Element a : doc.select("a[href]")) {
                String href = a.absUrl("href");
                if (href.isEmpty()) continue;
                String path = pathOf(href);
                if (!FEED_HREF.matcher(path).find()) continue;
                if (host != null && !host.equals(hostOf(href))) continue;
                out.add(href);
                if (out.size() >= MAX_CANDIDATES) break;
            }
        }
        return new ArrayList<>(out);
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    private static String pathOf(String url) {
        try {
            String p = new URI(url).getPath();
            return p == null ? "" : p;
        } catch (Exception e) {
            return "";
        }
    }
}
