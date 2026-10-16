package com.newshub.app;

import android.app.Application;
import android.content.Context;

import com.newshub.app.ui.common.ThemeHelper;
import com.newshub.app.work.NewsNotifier;
import com.newshub.app.work.RefreshScheduler;

public class NewsHubApp extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        ThemeHelper.apply(container.settings.theme());
        NewsNotifier.createChannel(this);
        container.io.execute(() -> {
            container.sources.seedBuiltIns(container.readCatalog());
            // First launch: load news right after the source list exists.
            if (container.settings.lastRefreshAt() == 0) container.news.refreshBlocking(true);
        });
        RefreshScheduler.schedule(this, container.settings);
    }

    public AppContainer container() {
        return container;
    }

    public static AppContainer container(Context context) {
        return ((NewsHubApp) context.getApplicationContext()).container();
    }
}
