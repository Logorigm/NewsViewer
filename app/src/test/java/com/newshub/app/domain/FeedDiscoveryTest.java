package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.newshub.app.Fixtures;
import com.newshub.app.domain.parse.FeedDiscovery;

import org.junit.Test;

import java.util.List;

public class FeedDiscoveryTest {

    @Test
    public void findsAlternateLinks() {
        List<String> found = FeedDiscovery.discover(Fixtures.text("html_page.html"), "https://news.example.ru/");
        assertEquals(2, found.size());
        assertEquals("https://news.example.ru/rss/all.xml", found.get(0));
        assertEquals("https://news.example.ru/atom", found.get(1));
    }

    @Test
    public void fallsBackToSameHostAnchors() {
        List<String> found = FeedDiscovery.discover(Fixtures.text("html_page_links_only.html"), "https://site.example.com/");
        assertEquals(1, found.size());
        assertEquals("https://site.example.com/feed/", found.get(0));
    }

    @Test
    public void nothingFound() {
        assertTrue(FeedDiscovery.discover("<html><body>пусто</body></html>", "https://x.example/").isEmpty());
    }
}
