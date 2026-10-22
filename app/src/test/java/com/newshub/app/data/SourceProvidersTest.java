package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.newshub.app.Fixtures;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.net.HttpClientFactory;
import com.newshub.app.data.net.HttpFetcher;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.data.source.RssSourceProvider;
import com.newshub.app.data.source.TelegramProviderAccess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class SourceProvidersTest {

    private static final long NOW = 1_792_150_000_000L;
    private MockWebServer server;
    private HttpFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        fetcher = new HttpFetcher(HttpClientFactory.createApi(HttpClientFactory.createClient()), 10);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private SourceEntity source(String type, String address) {
        SourceEntity s = new SourceEntity();
        s.id = "s";
        s.name = "Источник";
        s.type = type;
        s.address = address;
        return s;
    }

    @Test
    public void rssFeedIsParsed() {
        server.enqueue(new MockResponse().setBody(Fixtures.text("rss2_basic.xml")).setHeader("ETag", "\"e1\""));
        ProviderResult r = new RssSourceProvider(fetcher).fetch(source("RSS", server.url("/rss").toString()), true, NOW);
        assertEquals(ProviderResult.Status.OK, r.status);
        assertEquals(4, r.items.size());
        assertEquals("\"e1\"", r.etag);
        assertNull(r.discoveredFeedUrl);
    }

    @Test
    public void websiteAddressDiscoversFeed() {
        String page = Fixtures.text("html_page.html").replace("/rss/all.xml", server.url("/rss/all.xml").toString());
        server.enqueue(new MockResponse().setBody(page).setHeader("Content-Type", "text/html; charset=utf-8"));
        server.enqueue(new MockResponse().setBody(Fixtures.text("atom_basic.xml")));
        ProviderResult r = new RssSourceProvider(fetcher).fetch(source("RSS", server.url("/").toString()), true, NOW);
        assertEquals(ProviderResult.Status.OK, r.status);
        assertEquals(2, r.items.size());
        assertEquals(server.url("/rss/all.xml").toString(), r.discoveredFeedUrl);
    }

    @Test
    public void websiteWithoutFeedIsNotAFeed() {
        server.enqueue(new MockResponse().setBody("<html><body>Нет ленты</body></html>"));
        ProviderResult r = new RssSourceProvider(fetcher).fetch(source("RSS", server.url("/").toString()), true, NOW);
        assertEquals(ProviderResult.ErrorKind.NOT_A_FEED, r.errorKind);
    }

    @Test
    public void notModifiedIsPassedThrough() {
        server.enqueue(new MockResponse().setResponseCode(304));
        SourceEntity s = source("RSS", server.url("/rss").toString());
        s.etag = "\"e1\"";
        ProviderResult r = new RssSourceProvider(fetcher).fetch(s, true, NOW);
        assertEquals(ProviderResult.Status.NOT_MODIFIED, r.status);
    }

    @Test
    public void brokenDiscoveredUrlResetsDiscovery() {
        server.enqueue(new MockResponse().setResponseCode(404));
        SourceEntity s = source("RSS", server.url("/").toString());
        s.discoveredFeedUrl = server.url("/old-feed").toString();
        ProviderResult r = new RssSourceProvider(fetcher).fetch(s, true, NOW);
        assertTrue(r.isError());
        assertTrue(r.resetDiscovery);
    }

    @Test
    public void garbageIsParseError() {
        server.enqueue(new MockResponse().setBody("<?xml version='1.0'?><unknown><x/></unknown>"));
        ProviderResult r = new RssSourceProvider(fetcher).fetch(source("RSS", server.url("/rss").toString()), true, NOW);
        assertEquals(ProviderResult.ErrorKind.NOT_A_FEED, r.errorKind);
    }

    @Test
    public void networkErrorDoesNotThrow() throws Exception {
        String dead = server.url("/rss").toString();
        server.shutdown();
        ProviderResult r = new RssSourceProvider(fetcher).fetch(source("RSS", dead), true, NOW);
        assertTrue(r.isError());
        assertTrue(r.errorKind == ProviderResult.ErrorKind.NETWORK || r.errorKind == ProviderResult.ErrorKind.TIMEOUT);
    }

    @Test
    public void telegramPreviewIsParsed() throws Exception {
        server.enqueue(new MockResponse().setBody(Fixtures.text("telegram_preview.html")));
        ProviderResult r = TelegramProviderAccess.create(fetcher, server.url("/s/").toString())
                .fetch(source("TELEGRAM", "@testchannel"), true, NOW);
        assertEquals(ProviderResult.Status.OK, r.status);
        assertEquals(2, r.items.size());
        assertEquals("/s/testchannel", server.takeRequest().getPath());
    }

    @Test
    public void telegramInvalidHandleIsConfigError() {
        ProviderResult r = TelegramProviderAccess.create(fetcher, server.url("/s/").toString())
                .fetch(source("TELEGRAM", "https://t.me/+private"), true, NOW);
        assertEquals(ProviderResult.ErrorKind.CONFIG, r.errorKind);
        assertFalse(r.items.size() > 0);
    }
}
