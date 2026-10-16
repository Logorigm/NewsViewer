package com.newshub.app.domain.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;

/** Converts feed HTML fragments to plain text and extracts images. */
public final class HtmlText {

    private HtmlText() {
    }

    /** Maximum excerpt length kept for a publication (copyright: only a short fragment). */
    public static final int SNIPPET_LIMIT = 400;

    public static String toPlainText(String html) {
        if (html == null || html.isEmpty()) return "";
        if (html.indexOf('<') < 0 && html.indexOf('&') < 0) {
            return TextNormalizer.collapseWhitespace(html);
        }
        Document doc = Jsoup.parseBodyFragment(html);
        doc.select("script, style, iframe, noscript, figure figcaption").remove();
        return TextNormalizer.collapseWhitespace(doc.body().text());
    }

    /** Cuts the text at a word boundary so that it is not longer than {@code max} characters. */
    public static String snippet(String text, int max) {
        if (text == null) return "";
        String t = text.trim();
        if (t.length() <= max) return t;
        int cut = t.lastIndexOf(' ', max - 1);
        if (cut < max / 2) cut = max - 1;
        String head = t.substring(0, cut);
        while (!head.isEmpty() && ",.;:—–-".indexOf(head.charAt(head.length() - 1)) >= 0) {
            head = head.substring(0, head.length() - 1);
        }
        return head + "…";
    }

    /** First meaningful image in an HTML fragment, resolved against {@code baseUrl}. */
    public static String firstImage(String html, String baseUrl) {
        if (html == null || html.indexOf("<img") < 0 && html.indexOf("<IMG") < 0) return null;
        Document doc = Jsoup.parseBodyFragment(html, baseUrl == null ? "" : baseUrl);
        for (Element img : doc.select("img[src]")) {
            String w = img.attr("width");
            String h = img.attr("height");
            if ("1".equals(w) || "1".equals(h)) continue;
            String src = img.absUrl("src");
            if (src.isEmpty()) src = img.attr("src");
            if (isUsableImageUrl(src)) return src;
        }
        return null;
    }

    public static boolean isUsableImageUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false;
        return !(lower.contains("pixel") || lower.contains("counter") || lower.contains("/stat/")
                || lower.endsWith(".svg"));
    }
}
