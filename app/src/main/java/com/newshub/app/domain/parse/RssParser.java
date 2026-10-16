package com.newshub.app.domain.parse;

import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.text.HtmlText;
import com.newshub.app.domain.text.TextNormalizer;
import com.newshub.app.domain.text.UrlNormalizer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for RSS 2.0, RSS 1.0 (RDF) and Atom. Uses jsoup's lenient XML parser so that typical
 * real-world defects (HTML entities such as &amp;nbsp;, unescaped markup in descriptions, unclosed
 * tags, junk before the root element) do not break the whole feed.
 */
public final class RssParser {

    /** Hard limit on items taken from one document. */
    public static final int MAX_ITEMS = 200;
    private static final int TITLE_FROM_SUMMARY_LIMIT = 120;

    public ParsedFeed parse(byte[] body, String httpCharset, String baseUrl, long now)
            throws FeedParseException {
        if (body == null || body.length == 0 || isBlank(body)) {
            throw new FeedParseException(FeedParseException.Kind.EMPTY, "Пустой ответ сервера");
        }
        String charset = hasXmlEncodingDeclaration(body) ? null : httpCharset;
        Document doc;
        try {
            doc = Jsoup.parse(new ByteArrayInputStream(body), charset, baseUrl == null ? "" : baseUrl,
                    Parser.xmlParser());
        } catch (IOException | RuntimeException e) {
            throw new FeedParseException(FeedParseException.Kind.MALFORMED,
                    "Не удалось прочитать XML: " + e.getMessage());
        }
        return parseDocument(doc, baseUrl, now);
    }

    public ParsedFeed parse(String xml, String baseUrl, long now) throws FeedParseException {
        if (xml == null || xml.trim().isEmpty()) {
            throw new FeedParseException(FeedParseException.Kind.EMPTY, "Пустой ответ сервера");
        }
        Document doc = Jsoup.parse(xml, baseUrl == null ? "" : baseUrl, Parser.xmlParser());
        return parseDocument(doc, baseUrl, now);
    }

    private ParsedFeed parseDocument(Document doc, String baseUrl, long now) throws FeedParseException {
        Element root = null;
        for (Element child : doc.children()) {
            root = child;
            break;
        }
        if (root == null) {
            throw new FeedParseException(FeedParseException.Kind.MALFORMED, "В документе нет XML-элементов");
        }
        String rootName = localName(root);
        ParsedFeed feed = new ParsedFeed();
        List<Element> entries;
        boolean atom = false;
        switch (rootName) {
            case "rss": {
                Element channel = firstChild(root, "channel");
                Element container = channel != null ? channel : root;
                feed.title = titleText(firstChild(container, "title"));
                feed.format = "RSS 2.0";
                entries = children(container, "item");
                if (entries.isEmpty()) entries = children(root, "item");
                if (entries.isEmpty()) entries = descendants(root, "item");
                break;
            }
            case "rdf": {
                Element channel = firstChild(root, "channel");
                feed.title = channel != null ? titleText(firstChild(channel, "title")) : "";
                feed.format = "RSS 1.0";
                entries = children(root, "item");
                if (entries.isEmpty() && channel != null) entries = children(channel, "item");
                if (entries.isEmpty()) entries = descendants(root, "item");
                break;
            }
            case "feed": {
                feed.title = HtmlText.toPlainText(rawText(firstChild(root, "title")));
                feed.format = "Atom";
                entries = children(root, "entry");
                if (entries.isEmpty()) entries = descendants(root, "entry");
                atom = true;
                break;
            }
            case "html":
            case "head":
            case "body":
                throw new FeedParseException(FeedParseException.Kind.NOT_A_FEED,
                        "По адресу находится HTML-страница, а не RSS-лента");
            default:
                throw new FeedParseException(FeedParseException.Kind.NOT_A_FEED,
                        "Неизвестный формат документа: <" + root.tagName() + ">");
        }

        for (Element entry : entries) {
            if (feed.items.size() >= MAX_ITEMS) break;
            ParsedItem item = atom ? parseAtomEntry(entry, baseUrl, now) : parseRssItem(entry, baseUrl, now);
            if (item != null) feed.items.add(item);
        }
        return feed;
    }

    private ParsedItem parseRssItem(Element el, String baseUrl, long now) {
        ParsedItem it = new ParsedItem();
        String title = titleText(firstChild(el, "title"));

        String link = text(firstChild(el, "link"));
        if (link.isEmpty()) {
            List<Element> nested = descendants(el, "link");
            if (!nested.isEmpty()) link = nested.get(0).ownText().trim();
        }
        if (link.isEmpty()) {
            Element atomLink = firstChild(el, "atom:link");
            if (atomLink != null) link = atomLink.attr("href").trim();
        }
        Element guidEl = firstChild(el, "guid");
        String guid = guidEl != null ? text(guidEl) : "";
        if (link.isEmpty() && guidEl != null && !"false".equalsIgnoreCase(guidEl.attr("isPermaLink"))
                && guid.startsWith("http")) {
            link = guid;
        }
        link = UrlNormalizer.resolve(baseUrl, link);

        String description = rawText(firstChild(el, "description"));
        String content = rawText(firstChild(el, "content:encoded", "yandex:full-text", "turbo:content"));
        String summary = HtmlText.toPlainText(!description.isEmpty() ? description : content);

        it.imageUrl = findMediaImage(el, link.isEmpty() ? baseUrl : link);
        if (it.imageUrl == null) it.imageUrl = HtmlText.firstImage(content, link.isEmpty() ? baseUrl : link);
        if (it.imageUrl == null) it.imageUrl = HtmlText.firstImage(description, link.isEmpty() ? baseUrl : link);

        it.publishedAt = DateParser.sanitize(DateParser.parse(
                text(firstChild(el, "pubdate", "dc:date", "published", "updated", "a10:updated"))), now);
        String author = text(firstChild(el, "dc:creator", "author"));
        it.author = author.isEmpty() ? null : author;
        for (Element c : children(el, "category")) {
            String tag = text(c);
            if (!tag.isEmpty()) it.tags.add(tag);
        }
        return finish(it, title, summary, link, guid);
    }

    private ParsedItem parseAtomEntry(Element el, String baseUrl, long now) {
        ParsedItem it = new ParsedItem();
        String title = TextNormalizer.collapseWhitespace(HtmlText.toPlainText(rawText(firstChild(el, "title"))));
        String link = "";
        boolean alternateIsHtml = false;
        for (Element l : children(el, "link")) {
            String rel = l.attr("rel").trim().toLowerCase(Locale.ROOT);
            String href = l.attr("href").trim();
            if (href.isEmpty()) continue;
            if (rel.isEmpty() || rel.equals("alternate")) {
                boolean html = l.attr("type").isEmpty() || l.attr("type").contains("html");
                if (link.isEmpty() || html && !alternateIsHtml) {
                    link = href;
                    alternateIsHtml = html;
                }
            } else if (rel.equals("enclosure") && l.attr("type").startsWith("image/") && it.imageUrl == null) {
                it.imageUrl = UrlNormalizer.resolve(baseUrl, href);
            }
        }
        link = UrlNormalizer.resolve(baseUrl, link);
        String guid = text(firstChild(el, "id"));

        String summaryHtml = rawText(firstChild(el, "summary"));
        Element contentEl = firstChild(el, "content");
        String contentHtml = contentEl == null ? ""
                : ("xhtml".equalsIgnoreCase(contentEl.attr("type")) ? contentEl.html() : rawText(contentEl));
        String summary = HtmlText.toPlainText(!summaryHtml.isEmpty() ? summaryHtml : contentHtml);

        if (it.imageUrl == null) it.imageUrl = findMediaImage(el, link.isEmpty() ? baseUrl : link);
        if (it.imageUrl == null) it.imageUrl = HtmlText.firstImage(contentHtml, link.isEmpty() ? baseUrl : link);
        if (it.imageUrl == null) it.imageUrl = HtmlText.firstImage(summaryHtml, link.isEmpty() ? baseUrl : link);

        it.publishedAt = DateParser.sanitize(DateParser.parse(
                text(firstChild(el, "published", "issued", "updated", "modified"))), now);
        Element author = firstChild(el, "author");
        if (author != null) {
            String name = text(firstChild(author, "name"));
            it.author = name.isEmpty() ? null : name;
        }
        for (Element c : children(el, "category")) {
            String tag = c.hasAttr("label") ? c.attr("label") : c.attr("term");
            if (!tag.trim().isEmpty()) it.tags.add(tag.trim());
        }
        return finish(it, title, summary, link, guid);
    }

    private ParsedItem finish(ParsedItem it, String title, String summary, String link, String guid) {
        if (title.isEmpty() && summary.isEmpty()) return null;
        if (title.isEmpty()) {
            title = HtmlText.snippet(summary, TITLE_FROM_SUMMARY_LIMIT);
        }
        if (summary.equalsIgnoreCase(title)) summary = "";
        it.title = title;
        it.summary = HtmlText.snippet(summary, HtmlText.SNIPPET_LIMIT);
        it.link = link;
        if (!guid.isEmpty()) {
            it.guid = guid;
        } else if (!link.isEmpty()) {
            it.guid = link;
        } else {
            it.guid = title + "|" + it.publishedAt;
        }
        return it;
    }

    private String findMediaImage(Element item, String base) {
        String fallback = null;
        for (Element c : item.children()) {
            String name = c.tagName().toLowerCase(Locale.ROOT);
            switch (name) {
                case "media:content": {
                    String url = c.attr("url").trim();
                    String medium = c.attr("medium");
                    String type = c.attr("type");
                    if (!url.isEmpty() && ("image".equalsIgnoreCase(medium) || type.startsWith("image/")
                            || (medium.isEmpty() && type.isEmpty() && looksLikeImage(url)))) {
                        return UrlNormalizer.resolve(base, url);
                    }
                    String thumb = nestedThumbnail(c);
                    if (thumb != null && fallback == null) fallback = UrlNormalizer.resolve(base, thumb);
                    break;
                }
                case "media:thumbnail": {
                    String url = c.attr("url").trim();
                    if (!url.isEmpty() && fallback == null) fallback = UrlNormalizer.resolve(base, url);
                    break;
                }
                case "media:group": {
                    String nested = findMediaImage(c, base);
                    if (nested != null) return nested;
                    break;
                }
                case "enclosure": {
                    String url = c.attr("url").trim();
                    String type = c.attr("type").trim();
                    if (!url.isEmpty() && (type.startsWith("image/") || (type.isEmpty() && looksLikeImage(url)))) {
                        return UrlNormalizer.resolve(base, url);
                    }
                    break;
                }
                case "image": {
                    String url = c.attr("url").trim();
                    if (url.isEmpty()) url = c.attr("href").trim();
                    if (url.isEmpty()) {
                        Element u = firstChild(c, "url");
                        url = u != null ? text(u) : text(c);
                    }
                    if (url.startsWith("http") && fallback == null) fallback = UrlNormalizer.resolve(base, url);
                    break;
                }
                default:
                    break;
            }
        }
        return HtmlText.isUsableImageUrl(fallback) ? fallback : null;
    }

    private String nestedThumbnail(Element mediaContent) {
        Element t = firstChild(mediaContent, "media:thumbnail");
        if (t == null) return null;
        String url = t.attr("url").trim();
        return url.isEmpty() ? null : url;
    }

    static boolean looksLikeImage(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0) lower = lower.substring(0, q);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".gif");
    }

    // ---- small DOM helpers --------------------------------------------------------------------

    static String localName(Element e) {
        String n = e.tagName().toLowerCase(Locale.ROOT);
        int colon = n.indexOf(':');
        return colon >= 0 ? n.substring(colon + 1) : n;
    }

    static Element firstChild(Element parent, String... names) {
        if (parent == null) return null;
        for (String wanted : names) {
            for (Element c : parent.children()) {
                if (c.tagName().toLowerCase(Locale.ROOT).equals(wanted)) return c;
            }
        }
        return null;
    }

    static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        if (parent == null) return out;
        for (Element c : parent.children()) {
            if (c.tagName().toLowerCase(Locale.ROOT).equals(name)) out.add(c);
        }
        return out;
    }

    static List<Element> descendants(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Element e : parent.getAllElements()) {
            if (e != parent && e.tagName().toLowerCase(Locale.ROOT).equals(name)) out.add(e);
        }
        return out;
    }

    /**
     * Headline text. In broken documents an unclosed &lt;title&gt; swallows the following elements,
     * so only the element's own text is used in that case.
     */
    static String titleText(Element e) {
        if (e == null) return "";
        String raw = e.children().isEmpty() ? e.wholeText() : e.ownText();
        String t = TextNormalizer.collapseWhitespace(HtmlText.toPlainText(raw));
        return t.length() > 300 ? HtmlText.snippet(t, 300) : t;
    }

    static String text(Element e) {
        return e == null ? "" : TextNormalizer.collapseWhitespace(e.text());
    }

    /** Raw content of an element: CDATA/escaped HTML as a string, or inner markup if unescaped. */
    static String rawText(Element e) {
        if (e == null) return "";
        if (!e.children().isEmpty()) return e.html().trim();
        return e.wholeText().trim();
    }

    private static boolean isBlank(byte[] body) {
        for (byte b : body) {
            if (b != ' ' && b != '\n' && b != '\r' && b != '\t') return false;
        }
        return true;
    }

    private static boolean hasXmlEncodingDeclaration(byte[] body) {
        int n = Math.min(body.length, 256);
        String head = new String(body, 0, n, StandardCharsets.ISO_8859_1);
        int decl = head.indexOf("<?xml");
        if (decl < 0) return false;
        int end = head.indexOf("?>", decl);
        String declaration = end > 0 ? head.substring(decl, end) : head.substring(decl);
        return declaration.contains("encoding");
    }
}
