package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.PoliticsLimiter;
import com.newshub.app.domain.feed.PoliticsMode;
import com.newshub.app.domain.text.HtmlText;
import com.newshub.app.domain.text.TextNormalizer;
import com.newshub.app.domain.text.TimeFormat;
import com.newshub.app.domain.text.UrlNormalizer;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

public class TextUtilsTest {

    @Test
    public void normalize() {
        assertEquals("ежик привет 2026", TextNormalizer.normalize("Ёжик, «Привет»! — 2026"));
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("Текст новости", TextNormalizer.stripLabels("Молния: Срочно | Текст новости"));
        assertEquals(Arrays.asList("7.5", "2026"), new java.util.ArrayList<>(TextNormalizer.numbers("Рост 7,5% в 2026")));
        assertEquals(Arrays.asList("ракет"), TextNormalizer.searchTerms("Ракеты"));
    }

    @Test
    public void entitiesAreProperNouns() {
        assertTrue(TextNormalizer.entities("Учёные из МГУ создали робота в Казани").contains("мгу"));
        assertFalse(TextNormalizer.entities("Учёные из МГУ создали робота в Казани").contains("учены"));
    }

    @Test
    public void urlCanonicalization() {
        assertEquals("example.org/news/1?id=5",
                UrlNormalizer.canonical("https://www.example.org/news/1/?utm_source=rss&id=5#top"));
        assertEquals(UrlNormalizer.canonical("http://m.example.org/a/amp"), UrlNormalizer.canonical("https://example.org/a"));
        assertEquals("https://site.ru", UrlNormalizer.normalizeUserUrl("http://site.ru"));
        assertEquals("https://site.ru/rss", UrlNormalizer.normalizeUserUrl("site.ru/rss"));
        assertTrue(UrlNormalizer.isHttpsUrl("https://site.ru/rss"));
        assertFalse(UrlNormalizer.isHttpsUrl("https://localhost/rss"));
        assertFalse(UrlNormalizer.isHttpsUrl("not a url"));
    }

    @Test
    public void htmlToText() {
        assertEquals("Привет мир", HtmlText.toPlainText("<p>Привет&nbsp;<b>мир</b></p><script>x()</script>"));
        assertEquals("a b…", HtmlText.snippet("a b cdefgh", 6));
        assertEquals(null, HtmlText.firstImage("<img src='https://x.ru/pixel.gif' width='1'>", ""));
    }

    @Test
    public void localTime() {
        ZoneId msk = ZoneId.of("Europe/Moscow");
        long now = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli(); // 15:00 MSK
        assertEquals("только что", TimeFormat.relative(now - 10_000, now, msk));
        assertEquals("5 мин назад", TimeFormat.relative(now - 5 * 60_000, now, msk));
        assertEquals("2 ч назад", TimeFormat.relative(now - 2 * 3_600_000L, now, msk));
        assertEquals("вчера, 14:00", TimeFormat.relative(Instant.parse("2026-10-15T11:00:00Z").toEpochMilli(), now, msk));
        assertEquals("3 окт, 09:05", TimeFormat.relative(Instant.parse("2026-10-03T06:05:00Z").toEpochMilli(), now, msk));
        assertEquals("16 октября 2026, 15:00", TimeFormat.full(now, msk));
    }

    @Test
    public void politicsLimiterKeepsShare() {
        List<Boolean> items = Arrays.asList(true, false, false, false, false, true, true, false, false, false, true);
        List<Boolean> out = PoliticsLimiter.cap(items, b -> b, 0.2);
        int political = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i)) political++;
            assertTrue(political <= 0.2 * (i + 1) + 1e-9);
        }
        assertEquals(1, political);
    }

    @Test
    public void filterPoliticsSemantics() {
        FeedFilter base = FeedFilter.defaults();
        assertTrue(base.limitsPolitics());
        assertFalse(base.hidesPolitics());
        assertTrue(base.withMode(FeedMode.NO_POLITICS, null).hidesPolitics());
        FeedFilter hide = base.withSettings(100, true, true, PoliticsMode.HIDE);
        assertTrue(hide.hidesPolitics());
        FeedFilter off = base.withSettings(100, true, false, PoliticsMode.HIDE);
        assertFalse(off.hidesPolitics());
        assertFalse(off.limitsPolitics());
        assertFalse(hide.withMode(FeedMode.POLITICAL_ONLY, null).hidesPolitics());
    }
}
