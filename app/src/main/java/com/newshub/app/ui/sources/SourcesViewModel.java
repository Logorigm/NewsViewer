package com.newshub.app.ui.sources;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.SourceCount;

import java.util.List;

public class SourcesViewModel extends AndroidViewModel {

    private final AppContainer c;
    public final LiveData<List<SourceEntity>> sources;
    public final LiveData<List<SourceCount>> counts;

    public SourcesViewModel(@NonNull Application app) {
        super(app);
        c = NewsHubApp.container(app);
        sources = c.sources.observeSources();
        counts = c.sources.observeArticleCounts();
    }

    public void setEnabled(String id, boolean enabled) {
        c.sources.setEnabled(id, enabled);
    }

    public void refreshAll() {
        c.news.refreshAsync(true);
    }

    public void resetBuiltIns(Runnable done) {
        c.sources.resetBuiltIns(c.readCatalog(), done);
    }
}
