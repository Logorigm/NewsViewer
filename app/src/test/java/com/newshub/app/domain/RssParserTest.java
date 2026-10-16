package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.newshub.app.Fixtures;
import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.parse.FeedParseException;
import com.newshub.app.domain.parse.RssParser;

import org.junit.Test;

import java.nio.charset.Charset;
import java.time.Instant;

public class RssParserTest {

    private static final long NOW = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli();
    private final RssParser parser = new RssParser();

    @Test
    public void parsesRss2WithCdataMediaAndEntities() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("rss2_basic.xml"), null, "https://example.org/rss", NOW);
        assertEquals("Тестовая лента", feed.title);
        assertEquals("RSS 2.0", feed.format);
        // the item with neither title nor description is skipped
        assertEquals(4, feed.items.size());

        ParsedItem first = feed.items.get(0);
        assertEquals("Учёные получили первое изображение экзопланеты", first.title);
        assertEquals("news-1", first.guid);
        assertEquals("https://example.org/news/1?utm_source=rss", first.link);
        assertEquals("Телескоп сделал снимок далёкой планеты.", first.summary);
        assertEquals("https://cdn.example.org/media/planet-big.jpg", first.imageUrl);
        assertEquals(Instant.parse("2026-10-16T07:15:00Z").toEpochMilli(), first.publishedAt);
        assertEquals("Иван Петров", first.author);
        assertEquals(1, first.tags.size());
        assertEquals("Наука", first.tags.get(0));
    }

    @Test
    public void resolvesRelativeLinksAndEscapedHtml() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("rss2_basic.xml"), null, "https://example.org/rss", NOW);
        ParsedItem second = feed.items.get(1);
        assertEquals("Обновление «Android» вышло для смартфонов", second.title);
        assertEquals("https://example.org/news/2", second.link);
        assertEquals("Компания выпустила обновление.", second.summary);
        assertEquals("https://cdn.example.org/img/android.png", second.imageUrl);
        // MSK is UTC+3
        assertEquals(Instant.parse("2026-10-16T06:00:00Z").toEpochMilli(), second.publishedAt);
        // without guid the link is the identifier
        assertEquals("https://example.org/news/2", second.guid);
    }

    @Test
    public void missingDateIsZeroAndImageComesFromContent() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("rss2_basic.xml"), null, "https://example.org/rss", NOW);
        ParsedItem third = feed.items.get(2);
        assertEquals(0, third.publishedAt);
        assertEquals("https://cdn.example.org/full.jpg", third.imageUrl);
    }

    @Test
    public void titleIsBuiltFromDescriptionWhenMissing() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("rss2_basic.xml"), null, "https://example.org/rss", NOW);
        ParsedItem last = feed.items.get(3);
        assertTrue(last.title.startsWith("Пост без заголовка"));
        assertTrue(last.title.length() <= 121);
    }

    @Test
    public void parsesAtom() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("atom_basic.xml"), null, "https://example.com/feed", NOW);
        assertEquals("Atom", feed.format);
        assertEquals(2, feed.items.size());
        ParsedItem a = feed.items.get(0);
        assertEquals("Запуск ракеты — успешно", a.title);
        assertEquals("https://example.com/a/1", a.link);
        assertEquals("https://example.com/a/1.jpg", a.imageUrl);
        assertEquals("tag:example.com,2026:1", a.guid);
        assertEquals("Ракета вывела спутник на орбиту.", a.summary);
        assertEquals(Instant.parse("2026-10-16T04:30:00Z").toEpochMilli(), a.publishedAt);
        assertEquals("Редакция", a.author);
        assertEquals("Космос", a.tags.get(0));

        ParsedItem b = feed.items.get(1);
        assertEquals("Содержимое XHTML", b.summary);
        assertEquals("https://example.com/thumb2.jpg", b.imageUrl);
    }

    @Test
    public void parsesRdf() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("rdf_basic.xml"), null, "https://example.net/rss", NOW);
        assertEquals("RSS 1.0", feed.format);
        assertEquals(2, feed.items.size());
        assertEquals("Первый RDF-материал", feed.items.get(0).title);
        assertEquals(Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(), feed.items.get(0).publishedAt);
    }

    @Test
    public void recoversFromMalformedXml() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.bytes("malformed.xml"), null, "https://broken.example/rss", NOW);
        assertFalse(feed.items.isEmpty());
        ParsedItem first = feed.items.get(0);
        assertEquals("Нормальный заголовок — с сущностями", first.title);
        assertTrue(first.summary.contains("Неэкранированный HTML"));
        assertEquals(Instant.parse("2026-10-14T10:00:00Z").toEpochMilli(), first.publishedAt);
    }

    @Test
    public void htmlPageIsReportedAsNotAFeed() {
        try {
            parser.parse(Fixtures.bytes("html_page.html"), null, "https://news.example.ru/", NOW);
            fail("HTML must not be accepted as a feed");
        } catch (FeedParseException e) {
            assertEquals(FeedParseException.Kind.NOT_A_FEED, e.kind);
        }
    }

    @Test
    public void emptyBodyIsReported() {
        try {
            parser.parse(new byte[]{' ', '\n'}, null, "https://x.example/", NOW);
            fail();
        } catch (FeedParseException e) {
            assertEquals(FeedParseException.Kind.EMPTY, e.kind);
        }
    }

    @Test
    public void decodesWindows1251FromXmlDeclaration() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><rss><channel><title>Лента</title>"
                + "<item><title>Привет, мир</title><link>https://cp.example/1</link></item></channel></rss>";
        byte[] bytes = xml.getBytes(Charset.forName("windows-1251"));
        // a wrong HTTP charset must not override the XML declaration
        ParsedFeed feed = parser.parse(bytes, "UTF-8", "https://cp.example/rss", NOW);
        assertEquals("Лента", feed.title);
        assertEquals("Привет, мир", feed.items.get(0).title);
    }

    @Test
    public void futureDatesAreClampedToNow() throws Exception {
        String xml = "<rss><channel><item><title>Из будущего</title><link>https://f.example/1</link>"
                + "<pubDate>Sat, 17 Oct 2026 12:00:00 +0000</pubDate></item></channel></rss>";
        ParsedFeed feed = parser.parse(xml, "https://f.example/", NOW);
        assertEquals(NOW, feed.items.get(0).publishedAt);
    }

    @Test
    public void limitsNumberOfItems() throws Exception {
        StringBuilder sb = new StringBuilder("<rss><channel>");
        for (int i = 0; i < RssParser.MAX_ITEMS + 50; i++) {
            sb.append("<item><title>Новость ").append(i).append("</title><link>https://l.example/")
                    .append(i).append("</link></item>");
        }
        sb.append("</channel></rss>");
        ParsedFeed feed = parser.parse(sb.toString(), "https://l.example/", NOW);
        assertEquals(RssParser.MAX_ITEMS, feed.items.size());
    }

    @Test
    public void summaryIsLimitedFragment() throws Exception {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200; i++) longText.append("слово ");
        String xml = "<rss><channel><item><title>Длинный текст</title><link>https://s.example/1</link><description>"
                + longText + "</description></item></channel></rss>";
        ParsedItem item = parser.parse(xml, "https://s.example/", NOW).items.get(0);
        assertTrue(item.summary.length() <= 401);
        assertTrue(item.summary.endsWith("…"));
        assertNull(item.imageUrl);
        assertNotNull(item.guid);
    }
}
