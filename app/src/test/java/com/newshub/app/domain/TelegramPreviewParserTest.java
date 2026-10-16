package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.newshub.app.Fixtures;
import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.model.ParsedItem;
import com.newshub.app.domain.parse.FeedParseException;
import com.newshub.app.domain.parse.TelegramPreviewParser;

import org.junit.Test;

import java.time.Instant;

public class TelegramPreviewParserTest {

    private static final long NOW = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli();
    private final TelegramPreviewParser parser = new TelegramPreviewParser();

    @Test
    public void normalizesHandles() {
        assertEquals("tproger", TelegramPreviewParser.normalizeHandle("@tproger"));
        assertEquals("tproger", TelegramPreviewParser.normalizeHandle("tproger"));
        assertEquals("tproger", TelegramPreviewParser.normalizeHandle("https://t.me/tproger"));
        assertEquals("tproger", TelegramPreviewParser.normalizeHandle("t.me/s/tproger"));
        assertEquals("Rozetked", TelegramPreviewParser.normalizeHandle("https://t.me/Rozetked/12345"));
        assertNull(TelegramPreviewParser.normalizeHandle("https://t.me/+AbCdEf"));
        assertNull(TelegramPreviewParser.normalizeHandle("https://t.me/joinchat/xyz"));
        assertNull(TelegramPreviewParser.normalizeHandle("плохое имя"));
        assertNull(TelegramPreviewParser.normalizeHandle("ab"));
        assertNull(TelegramPreviewParser.normalizeHandle(""));
    }

    @Test
    public void parsesPostsNewestFirstAndSkipsMediaOnly() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.text("telegram_preview.html"), "testchannel", NOW);
        assertEquals("Тестовый канал", feed.title);
        assertEquals(2, feed.items.size());

        ParsedItem newest = feed.items.get(0);
        assertEquals("testchannel/103", newest.guid);

        ParsedItem linux = feed.items.get(1);
        assertEquals("testchannel/101", linux.guid);
        assertEquals("Вышла новая версия Linux-ядра", linux.title);
        assertTrue(linux.summary.startsWith("Главные изменения"));
        assertEquals("https://t.me/testchannel/101", linux.link);
        assertEquals("https://cdn4.telesco.pe/file/photo101.jpg", linux.imageUrl);
        assertEquals(Instant.parse("2026-10-16T08:00:00Z").toEpochMilli(), linux.publishedAt);
    }

    @Test
    public void longSingleLinePostIsSplitAtSentence() throws Exception {
        ParsedFeed feed = parser.parse(Fixtures.text("telegram_preview.html"), "testchannel", NOW);
        ParsedItem item = feed.items.get(0);
        assertTrue(item.title.endsWith("заголовком карточки."));
        assertTrue(item.title.length() <= 141);
        assertTrue(item.summary.startsWith("Второе предложение"));
    }

    @Test
    public void pageWithoutPreviewIsError() {
        try {
            parser.parse("<html><body><div class='tgme_page'>Contact</div></body></html>", "private_ch", NOW);
            fail();
        } catch (FeedParseException e) {
            assertEquals(FeedParseException.Kind.NOT_A_FEED, e.kind);
        }
    }
}
