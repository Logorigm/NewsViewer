package com.newshub.app.data.source;

import com.newshub.app.data.net.HttpFetcher;

/** Test access to the package-private constructor with a custom preview base URL. */
public final class TelegramProviderAccess {

    private TelegramProviderAccess() {
    }

    public static TelegramPreviewProvider create(HttpFetcher fetcher, String previewBase) {
        return new TelegramPreviewProvider(fetcher, previewBase);
    }
}
