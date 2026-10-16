package com.newshub.app;

import com.newshub.app.data.source.ProviderRegistry;

/** Release build: no demo data and no developer tools. */
public final class DevTools {

    private DevTools() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static void registerProviders(ProviderRegistry registry) {
        // demo provider exists only in debug builds
    }

    public static void setDemoEnabled(AppContainer container, boolean enabled, Runnable done) {
        if (done != null) done.run();
    }
}
