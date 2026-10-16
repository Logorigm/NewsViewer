package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.newshub.app.data.net.FetchResult;
import com.newshub.app.data.net.HttpClientFactory;
import com.newshub.app.data.net.HttpFetcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

public class HttpFetcherTest {

    private MockWebServer server;
    private HttpFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = HttpClientFactory.createClient().newBuilder()
                .readTimeout(700, TimeUnit.MILLISECONDS)
                .callTimeout(3, TimeUnit.SECONDS)
                .build();
        fetcher = new HttpFetcher(HttpClientFactory.createApi(client), 10);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    @Test
    public void okReturnsBodyCharsetAndValidators() throws Exception {
        server.enqueue(new MockResponse().setBody("<rss/>")
                .setHeader("Content-Type", "application/rss+xml; charset=windows-1251")
                .setHeader("ETag", "\"v1\"")
                .setHeader("Last-Modified", "Fri, 16 Oct 2026 10:00:00 GMT"));
        FetchResult r = fetcher.get(url("/feed"), null, null);
        assertEquals(FetchResult.Status.OK, r.status);
        assertEquals("<rss/>", new String(r.body, StandardCharsets.UTF_8));
        assertEquals("windows-1251", r.charset);
        assertEquals("\"v1\"", r.etag);
        assertEquals("Fri, 16 Oct 2026 10:00:00 GMT", r.lastModified);
        RecordedRequest req = server.takeRequest();
        assertEquals(HttpClientFactory.USER_AGENT, req.getHeader("User-Agent"));
        assertNull(req.getHeader("If-None-Match"));
    }

    @Test
    public void conditionalRequestGets304() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(304).setHeader("ETag", "\"v1\""));
        FetchResult r = fetcher.get(url("/feed"), "\"v1\"", "Fri, 16 Oct 2026 10:00:00 GMT");
        assertEquals(FetchResult.Status.NOT_MODIFIED, r.status);
        RecordedRequest req = server.takeRequest();
        assertEquals("\"v1\"", req.getHeader("If-None-Match"));
        assertEquals("Fri, 16 Oct 2026 10:00:00 GMT", req.getHeader("If-Modified-Since"));
    }

    @Test
    public void notFoundIsHttpErrorWithoutRetry() {
        server.enqueue(new MockResponse().setResponseCode(404));
        FetchResult r = fetcher.get(url("/missing"), null, null);
        assertEquals(FetchResult.Status.HTTP_ERROR, r.status);
        assertEquals(404, r.httpCode);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void rateLimitRespectsRetryAfter() {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "120"));
        FetchResult r = fetcher.get(url("/feed"), null, null);
        assertEquals(FetchResult.Status.RATE_LIMITED, r.status);
        assertEquals(120_000L, r.retryAfterMillis);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void serverErrorIsRetriedOnce() {
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setBody("ok"));
        FetchResult r = fetcher.get(url("/feed"), null, null);
        assertEquals(FetchResult.Status.OK, r.status);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void timeoutIsReported() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        FetchResult r = fetcher.get(url("/slow"), null, null);
        assertEquals(FetchResult.Status.TIMEOUT, r.status);
    }

    @Test
    public void tooLargeBodyIsRejected() {
        byte[] big = new byte[HttpFetcher.MAX_BODY_BYTES + 10];
        server.enqueue(new MockResponse().setBody(new okio.Buffer().write(big)));
        FetchResult r = fetcher.get(url("/big"), null, null);
        assertEquals(FetchResult.Status.TOO_LARGE, r.status);
    }

    @Test
    public void unreachableHostIsNetworkError() throws Exception {
        String dead = url("/x");
        server.shutdown();
        FetchResult r = fetcher.get(dead, null, null);
        assertTrue(r.status == FetchResult.Status.NETWORK_ERROR || r.status == FetchResult.Status.TIMEOUT);
    }
}
