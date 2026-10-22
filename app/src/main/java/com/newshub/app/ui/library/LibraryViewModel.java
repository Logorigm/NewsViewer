package com.newshub.app.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.db.model.FeedRow;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {

    private final AppContainer c;
    public final LiveData<List<FeedRow>> favorites;
    public final LiveData<List<FeedRow>> history;
    public int tab;

    public LibraryViewModel(@NonNull Application app) {
        super(app);
        c = NewsHubApp.container(app);
        favorites = c.news.observeFavorites();
        history = c.news.observeHistory();
    }

    public void toggleFavorite(long articleId) {
        c.news.toggleFavorite(articleId);
    }

    public void clearHistory() {
        c.news.clearHistory(null);
    }
}
