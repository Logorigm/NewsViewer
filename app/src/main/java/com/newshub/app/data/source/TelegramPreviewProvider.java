package com.newshub.app.data.source;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.net.FetchResult;
import com.newshub.app.data.net.HttpFetcher;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.model.ParsedFeed;
import com.newshub.app.domain.parse.FeedParseException;
import com.newshub.app.domain.parse.TelegramPreviewParser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads public Telegram channels through the public web preview (t.me/s/&lt;channel&gt;).
 *
 * <p>What this can and cannot do:
 * <ul>
 *   <li>Only public channels with the web preview enabled; no login, no keys, nothing is sent
 *       on behalf of the user.</li>
 *   <li>Only the latest posts shown on that page (about 20); older history is not available.</li>
 *   <li>The Telegram Bot API cannot replace this: a bot receives channel posts only from channels
 *       where it was added as an administrator, so a bot token alone cannot read arbitrary
 *       channels.</li>
 *   <li>Reading private channels or full history requires the MTProto client API (TDLib) with an
 *       api_id/api_hash registered by the developer and the user's own phone login. That is not
 *       implemented, and keys must never be embedded in the APK.</li>
 * </ul>
 */
public final class TelegramPreviewProvider implements SourceProvider {

    private final HttpFetcher fetcher;
    private final String previewBase;
    private final TelegramPreviewParser parser = new TelegramPreviewParser();

    public TelegramPreviewProvider(HttpFetcher fetcher) {
        this(fetcher, "https://t.me/s/");
    }

    /** @param previewBase base of the preview pages (overridable for tests). */
    TelegramPreviewProvider(HttpFetcher fetcher, String previewBase) {
        this.fetcher = fetcher;
        this.previewBase = previewBase;
    }

    @Override
    public SourceType type() {
        return SourceType.TELEGRAM;
    }

    @Override
    public ProviderResult fetch(SourceEntity source, boolean conditional, long now) {
        String handle = TelegramPreviewParser.normalizeHandle(source.address);
        if (handle == null) {
            return ProviderResult.error(ProviderResult.ErrorKind.CONFIG,
                    "Укажите публичное имя канала, например @channel");
        }
        FetchResult r = fetcher.get(previewBase + handle, null, null);
        if (!r.isOk()) return ProviderErrors.fromFetch(r);
        if (r.finalUrl != null && !r.finalUrl.contains("/s/")) {
            return ProviderResult.error(ProviderResult.ErrorKind.NOT_A_FEED,
                    "Канал не публикует веб-превью (возможно, он закрытый)");
        }
        Charset cs = StandardCharsets.UTF_8;
        if (r.charset != null) {
            try {
                cs = Charset.forName(r.charset);
            } catch (Exception ignored) {
                // keep UTF-8
            }
        }
        try {
            ParsedFeed feed = parser.parse(new String(r.body, cs), handle, now);
            return ProviderResult.ok(feed.items, feed.title, null, null, null);
        } catch (FeedParseException e) {
            return ProviderErrors.fromParse(e);
        }
    }
}
