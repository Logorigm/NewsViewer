package com.newshub.app.data.source;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.net.FetchResult;
import com.newshub.app.data.net.HttpFetcher;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.parse.FeedDiscovery;
import com.newshub.app.domain.parse.FeedParseException;
import com.newshub.app.domain.parse.RssParser;

import java.util.List;

/**
 * RSS/Atom provider. The source address may be a feed URL or a website: if the address returns
 * an HTML page, the feed advertised by that page is discovered and remembered.
 */
public final class RssSourceProvider implements SourceProvider {

    private static final int MAX_DISCOVERY_CANDIDATES = 3;

    private final HttpFetcher fetcher;
    private final RssParser parser = new RssParser();

    public RssSourceProvider(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public SourceType type() {
        return SourceType.RSS;
    }

    @Override
    public ProviderResult fetch(SourceEntity source, boolean conditional, long now) {
        if (source.address.trim().isEmpty()) {
            return ProviderResult.error(ProviderResult.ErrorKind.CONFIG, "Не указан адрес ленты или сайта");
        }
        boolean usingDiscovered = source.discoveredFeedUrl != null && !source.discoveredFeedUrl.isEmpty();
        String url = usingDiscovered ? source.discoveredFeedUrl : source.address.trim();
        FetchResult r = fetcher.get(url, conditional ? source.etag : null, conditional ? source.lastModified : null);
        if (r.status == FetchResult.Status.NOT_MODIFIED) {
            return ProviderResult.notModified(r.etag, r.lastModified);
        }
        if (!r.isOk()) {
            ProviderResult err = ProviderErrors.fromFetch(r);
            if (usingDiscovered && (r.httpCode == 404 || r.httpCode == 410)) return err.withResetDiscovery();
            return err;
        }
        try {
            ParsedFeed feed = parser.parse(r.body, r.charset, r.finalUrl, now);
            return ProviderResult.ok(feed.items, feed.title, r.etag, r.lastModified, null);
        } catch (FeedParseException e) {
            if (e.kind != FeedParseException.Kind.NOT_A_FEED) return ProviderErrors.fromParse(e);
            if (usingDiscovered) return ProviderErrors.fromParse(e).withResetDiscovery();
            return discover(r, now);
        }
    }

    private ProviderResult discover(FetchResult page, long now) {
        List<String> candidates = FeedDiscovery.discover(page.body, page.charset, page.finalUrl);
        if (candidates.isEmpty()) {
            return ProviderResult.error(ProviderResult.ErrorKind.NOT_A_FEED,
                    "По адресу открывается сайт, но ссылка на RSS-ленту на нём не найдена");
        }
        ProviderResult lastError = null;
        int tried = 0;
        for (String candidate : candidates) {
            if (tried++ >= MAX_DISCOVERY_CANDIDATES) break;
            FetchResult r = fetcher.get(candidate, null, null);
            if (!r.isOk()) {
                lastError = ProviderErrors.fromFetch(r);
                continue;
            }
            try {
                ParsedFeed feed = parser.parse(r.body, r.charset, r.finalUrl, now);
                return ProviderResult.ok(feed.items, feed.title, r.etag, r.lastModified, r.finalUrl);
            } catch (FeedParseException e) {
                lastError = ProviderErrors.fromParse(e);
            }
        }
        return lastError != null ? lastError : ProviderResult.error(ProviderResult.ErrorKind.NOT_A_FEED,
                "Найденные на сайте ссылки не являются RSS-лентами");
    }
}
