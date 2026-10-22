package com.newshub.app.data.source;

import com.newshub.app.domain.SourceType;

import java.util.EnumMap;
import java.util.Map;

/** Maps source types to providers. */
public final class ProviderRegistry {

    private final Map<SourceType, SourceProvider> providers = new EnumMap<>(SourceType.class);

    public ProviderRegistry register(SourceProvider provider) {
        providers.put(provider.type(), provider);
        return this;
    }

    /** @return the provider, or null when the type is not supported in this build. */
    public SourceProvider get(SourceType type) {
        return providers.get(type);
    }
}
