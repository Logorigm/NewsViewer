package com.newshub.app.data.source;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.domain.SourceType;

/**
 * Common interface for every kind of source. A new kind of source (another API, a JSON feed,
 * etc.) is added by implementing this interface and registering it in {@link ProviderRegistry}.
 * Implementations run on a background thread and must never throw: every failure is reported
 * through {@link ProviderResult}.
 */
public interface SourceProvider {

    SourceType type();

    /**
     * @param conditional send ETag / Last-Modified from the previous fetch so the server can
     *                    answer "304 Not Modified" instead of the full document.
     */
    ProviderResult fetch(SourceEntity source, boolean conditional, long now);
}
