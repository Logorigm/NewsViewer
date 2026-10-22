package com.newshub.app.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.PoliticsMode;
import com.newshub.app.domain.feed.SortOrder;

import java.util.List;

/**
 * Search over stored titles and excerpts (works offline). Search ignores the politics filter so
 * that hidden publications can still be found.
 */
public class SearchViewModel extends AndroidViewModel {

    private static final int RESULT_LIMIT = 200;

    private final AppContainer c;
    private final MediatorLiveData<List<FeedRow>> results = new MediatorLiveData<>();
    private LiveData<List<FeedRow>> source;
    private String query = "";
    private Category category;

    public SearchViewModel(@NonNull Application app) {
        super(app);
        c = NewsHubApp.container(app);
    }

    public LiveData<List<FeedRow>> results() {
        return results;
    }

    public String query() {
        return query;
    }

    public Category category() {
        return category;
    }

    public boolean grouped() {
        return c.settings.groupDuplicates();
    }

    public void search(String q, Category cat) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.equals(query) && cat == category && source != null) return;
        query = trimmed;
        category = cat;
        if (source != null) results.removeSource(source);
        source = null;
        if (trimmed.length() < 2) {
            results.setValue(null);
            return;
        }
        FeedFilter f = new FeedFilter(cat == null ? FeedMode.ALL : FeedMode.CATEGORY, cat, Period.ALL,
                SortOrder.NEWEST, null, trimmed, RESULT_LIMIT, c.settings.groupDuplicates(), false, PoliticsMode.LIMIT);
        source = c.news.observeFeed(f, 0);
        results.addSource(source, results::setValue);
    }

    public void toggleFavorite(long articleId) {
        c.news.toggleFavorite(articleId);
    }
}
