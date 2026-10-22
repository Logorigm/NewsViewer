package com.newshub.app.data.source;

import com.newshub.app.domain.model.ParsedItem;

import java.util.Collections;
import java.util.List;

/** What a provider returns for one source. */
public final class ProviderResult {

    public enum Status { OK, NOT_MODIFIED, ERROR }

    public enum ErrorKind {
        NETWORK, TIMEOUT, HTTP, RATE_LIMITED, PARSE, NOT_A_FEED, TOO_LARGE, CONFIG, DISABLED, UNSUPPORTED
    }

    public final Status status;
    public final ErrorKind errorKind;
    public final String message;
    public final List<ParsedItem> items;
    public final String feedTitle;
    public final String etag;
    public final String lastModified;
    public final long retryAfterMillis;
    /** Feed URL found on the website (to be remembered for the next fetches), or null. */
    public final String discoveredFeedUrl;
    /** The remembered discovered URL stopped working and must be looked up again. */
    public final boolean resetDiscovery;

    private ProviderResult(Status status, ErrorKind errorKind, String message, List<ParsedItem> items,
                           String feedTitle, String etag, String lastModified, long retryAfterMillis,
                           String discoveredFeedUrl, boolean resetDiscovery) {
        this.status = status;
        this.errorKind = errorKind;
        this.message = message;
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
        this.feedTitle = feedTitle;
        this.etag = etag;
        this.lastModified = lastModified;
        this.retryAfterMillis = retryAfterMillis;
        this.discoveredFeedUrl = discoveredFeedUrl;
        this.resetDiscovery = resetDiscovery;
    }

    public static ProviderResult ok(List<ParsedItem> items, String feedTitle, String etag, String lastModified,
                                    String discoveredFeedUrl) {
        return new ProviderResult(Status.OK, null, null, items, feedTitle, etag, lastModified, 0, discoveredFeedUrl,
                false);
    }

    public static ProviderResult notModified(String etag, String lastModified) {
        return new ProviderResult(Status.NOT_MODIFIED, null, null, null, null, etag, lastModified, 0, null, false);
    }

    public static ProviderResult error(ErrorKind kind, String message) {
        return new ProviderResult(Status.ERROR, kind, message, null, null, null, null, 0, null, false);
    }

    public static ProviderResult rateLimited(String message, long retryAfterMillis) {
        return new ProviderResult(Status.ERROR, ErrorKind.RATE_LIMITED, message, null, null, null, null,
                retryAfterMillis, null, false);
    }

    public ProviderResult withResetDiscovery() {
        return new ProviderResult(status, errorKind, message, items, feedTitle, etag, lastModified, retryAfterMillis,
                discoveredFeedUrl, true);
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
