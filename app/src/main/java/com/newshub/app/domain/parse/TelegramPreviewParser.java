package com.newshub.app.domain.parse;

import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.text.HtmlText;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the public web preview of a Telegram channel (https://t.me/s/&lt;channel&gt;).
 *
 * <p>This is the page Telegram itself serves to anyone who opens a public channel in a browser.
 * It needs no login and no API keys, but it is not a documented API: only public channels that
 * have the preview enabled are available, only the latest ~20 posts are shown, and the markup
 * may change. Private channels and anything requiring an account need the MTProto client API
 * (TDLib) with the user's own authorization, which this app does not do.
 */
public final class TelegramPreviewParser {

    private static final Pattern HANDLE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,31}$");
    private static final Pattern BG_URL = Pattern.compile("background-image:\\s*url\\(['\"]?(.*?)['\"]?\\)");
    private static final int TITLE_LIMIT = 140;

    /**
     * Accepts "@name", "name", "t.me/name", "https://t.me/s/name", "https://telegram.me/name/123"
     * and returns the bare channel name, or null if the input is not a valid public username.
     */
    public static String normalizeHandle(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        s = s.replaceFirst("(?i)^https?://", "");
        s = s.replaceFirst("(?i)^(www\\.)?(t\\.me|telegram\\.me|telegram\\.dog)/", "");
        s = s.replaceFirst("^s/", "");
        if (s.startsWith("@")) s = s.substring(1);
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (s.startsWith("+") || s.equalsIgnoreCase("joinchat")) return null; // private invite links
        return HANDLE.matcher(s).matches() ? s : null;
    }

    public static String previewUrl(String handle) {
        return "https://t.me/s/" + handle;
    }

    public ParsedFeed parse(String html, String handle, long now) throws FeedParseException {
        if (html == null || html.trim().isEmpty()) {
            throw new FeedParseException(FeedParseException.Kind.EMPTY, "Пустой ответ сервера");
        }
        Document doc = Jsoup.parse(html, previewUrl(handle));
        ParsedFeed feed = new ParsedFeed();
        feed.format = "Telegram (веб-превью)";
        Element channelTitle = doc.selectFirst(".tgme_channel_info_header_title");
        if (channelTitle != null) feed.title = channelTitle.text().trim();

        List<Element> messages = doc.select("div.tgme_widget_message[data-post]");
        if (messages.isEmpty()) {
            if (doc.selectFirst(".tgme_channel_info") != null) {
                return feed; // channel exists but shows no posts
            }
            throw new FeedParseException(FeedParseException.Kind.NOT_A_FEED,
                    "У канала нет публичного веб-превью или он не найден");
        }

        List<ParsedItem> items = new ArrayList<>();
        for (Element msg : messages) {
            ParsedItem item = parseMessage(msg, handle, now);
            if (item != null) items.add(item);
        }
        Collections.reverse(items); // the page lists posts oldest first
        feed.items.addAll(items);
        return feed;
    }

    private ParsedItem parseMessage(Element msg, String handle, long now) {
        String post = msg.attr("data-post").trim(); // "channel/123"
        if (post.isEmpty()) return null;

        Element textEl = msg.selectFirst(".tgme_widget_message_bubble > .tgme_widget_message_text");
        if (textEl == null) textEl = msg.selectFirst(".js-message_text");
        String text = textEl == null ? "" : messageText(textEl);
        if (text.isEmpty()) return null; // media-only posts have no headline

        String[] parts = splitTitle(text);
        ParsedItem item = new ParsedItem();
        item.guid = post;
        item.title = parts[0];
        item.summary = HtmlText.snippet(parts[1], HtmlText.SNIPPET_LIMIT);

        Element dateLink = msg.selectFirst("a.tgme_widget_message_date");
        String link = dateLink != null ? dateLink.absUrl("href") : "";
        if (link.isEmpty()) link = "https://t.me/" + post;
        item.link = link;

        Element time = msg.selectFirst("time[datetime]");
        if (time != null) item.publishedAt = DateParser.sanitize(DateParser.parse(time.attr("datetime")), now);

        item.imageUrl = backgroundImage(msg.selectFirst("a.tgme_widget_message_photo_wrap"));
        if (item.imageUrl == null) item.imageUrl = backgroundImage(msg.selectFirst(".tgme_widget_message_video_thumb"));
        if (item.imageUrl == null) item.imageUrl = backgroundImage(msg.selectFirst(".link_preview_image"));
        if (item.imageUrl == null) item.imageUrl = backgroundImage(msg.selectFirst(".link_preview_right_image"));

        Element author = msg.selectFirst(".tgme_widget_message_owner_name");
        if (author != null) item.author = author.text().trim();
        if (item.author == null || item.author.isEmpty()) item.author = "@" + handle.toLowerCase(Locale.ROOT);
        return item;
    }

    static String messageText(Element textEl) {
        Element copy = textEl.clone();
        for (Element br : copy.select("br")) br.replaceWith(new TextNode("\n"));
        String raw = copy.wholeText().replace(' ', ' ');
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            String l = line.replaceAll("[ \\t]+", " ").trim();
            if (l.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            sb.append(l);
        }
        return sb.toString().trim();
    }

    /** Splits a post into a headline (first line or first sentence) and the rest of the text. */
    static String[] splitTitle(String text) {
        String[] lines = text.split("\n");
        int idx = 0;
        String first = "";
        while (idx < lines.length) {
            first = stripLeadingSymbols(lines[idx]);
            idx++;
            if (!first.isEmpty()) break;
        }
        StringBuilder rest = new StringBuilder();
        for (int i = idx; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.isEmpty()) continue;
            if (rest.length() > 0) rest.append(' ');
            rest.append(l);
        }
        if (first.length() <= TITLE_LIMIT) {
            return new String[]{trimTrailingColon(first), rest.toString()};
        }
        int end = sentenceEnd(first, 40, TITLE_LIMIT);
        if (end > 0) {
            String title = first.substring(0, end + 1).trim();
            String remainder = first.substring(end + 1).trim();
            String r = remainder + (rest.length() > 0 ? " " + rest : "");
            return new String[]{title, r.trim()};
        }
        String title = HtmlText.snippet(first, 120);
        return new String[]{title, (first + (rest.length() > 0 ? " " + rest : "")).trim()};
    }

    private static int sentenceEnd(String s, int min, int max) {
        int best = -1;
        for (int i = min; i < Math.min(s.length() - 1, max); i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' ') best = i;
        }
        return best;
    }

    private static String stripLeadingSymbols(String line) {
        String l = line.trim();
        int i = 0;
        while (i < l.length()) {
            int cp = l.codePointAt(i);
            if (Character.isLetterOrDigit(cp) || cp == '«' || cp == '"' || cp == '(') break;
            i += Character.charCount(cp);
        }
        return l.substring(i).trim();
    }

    private static String trimTrailingColon(String s) {
        String t = s.trim();
        while (t.endsWith(":")) t = t.substring(0, t.length() - 1).trim();
        return t;
    }

    private static String backgroundImage(Element el) {
        if (el == null) return null;
        Matcher m = BG_URL.matcher(el.attr("style"));
        if (!m.find()) return null;
        String url = m.group(1).trim();
        if (url.startsWith("//")) url = "https:" + url;
        return HtmlText.isUsableImageUrl(url) ? url : null;
    }
}
