package com.newshub.app.data.source;

import com.newshub.app.data.net.FetchResult;
import com.newshub.app.domain.parse.FeedParseException;

/** Maps transport and parser failures to provider results. */
final class ProviderErrors {

    private ProviderErrors() {
    }

    static ProviderResult fromFetch(FetchResult r) {
        switch (r.status) {
            case RATE_LIMITED:
                return ProviderResult.rateLimited(r.message, r.retryAfterMillis);
            case TIMEOUT:
                return ProviderResult.error(ProviderResult.ErrorKind.TIMEOUT, r.message);
            case NETWORK_ERROR:
                return ProviderResult.error(ProviderResult.ErrorKind.NETWORK, r.message);
            case TOO_LARGE:
                return ProviderResult.error(ProviderResult.ErrorKind.TOO_LARGE, r.message);
            case HTTP_ERROR:
            default:
                return ProviderResult.error(ProviderResult.ErrorKind.HTTP, r.message);
        }
    }

    static ProviderResult fromParse(FeedParseException e) {
        if (e.kind == FeedParseException.Kind.NOT_A_FEED) {
            return ProviderResult.error(ProviderResult.ErrorKind.NOT_A_FEED, e.getMessage());
        }
        return ProviderResult.error(ProviderResult.ErrorKind.PARSE, e.getMessage());
    }
}
