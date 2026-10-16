package com.newshub.app;

import android.content.Context;

import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.net.HttpClientFactory;
import com.newshub.app.data.net.HttpFetcher;
import com.newshub.app.data.repo.NewsRepository;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.data.repo.SourceRepository;
import com.newshub.app.data.source.ProviderRegistry;
import com.newshub.app.data.source.RssSourceProvider;
import com.newshub.app.data.source.TelegramPreviewProvider;
import com.newshub.app.work.RefreshScheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

/**
 * Manual dependency container (one instance per process). Kept deliberately simple instead of a
 * DI framework: the graph is small and this keeps the Java build free of extra annotation
 * processing.
 */
public final class AppContainer {

    public final Context appContext;
    public final NewsDatabase db;
    public final OkHttpClient httpClient;
    public final HttpFetcher fetcher;
    public final ProviderRegistry registry;
    public final ExecutorService io;
    public final SettingsRepository settings;
    public final SourceRepository sources;
    public final NewsRepository news;

    public AppContainer(Context context) {
        appContext = context.getApplicationContext();
        db = NewsDatabase.create(appContext);
        httpClient = HttpClientFactory.createClient();
        fetcher = new HttpFetcher(HttpClientFactory.createApi(httpClient));
        registry = new ProviderRegistry()
                .register(new RssSourceProvider(fetcher))
                .register(new TelegramPreviewProvider(fetcher));
        DevTools.registerProviders(registry);
        io = Executors.newFixedThreadPool(3);
        settings = new SettingsRepository(db.settings());
        sources = new SourceRepository(db, registry, io);
        news = new NewsRepository(db, registry, settings, io);

        settings.addListener(key -> {
            if (SettingsRepository.POLITICS_KEYS.contains(key)) {
                news.reclassifyPoliticsAsync();
            } else if (SettingsRepository.REFRESH_INTERVAL.equals(key) || SettingsRepository.WIFI_ONLY.equals(key)) {
                RefreshScheduler.schedule(appContext, settings);
            }
        });
    }

    public String readCatalog() {
        try (InputStream in = appContext.getAssets().open("sources.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "{\"sources\":[]}";
        }
    }
}
