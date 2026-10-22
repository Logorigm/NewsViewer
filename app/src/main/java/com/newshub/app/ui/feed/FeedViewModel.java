package com.newshub.app.ui.feed;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.PoliticsLimiter;
import com.newshub.app.domain.feed.SortOrder;
import com.newshub.app.domain.rank.InterestRanker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * State of one feed screen. Rows come from Room as LiveData (so favorites, new items and source
 * toggles update the list automatically) and are post-processed off the main thread: ranking for
 * "Только интересное" and the politics share limit.
 */
public class FeedViewModel extends AndroidViewModel {

    /** Candidates considered by the "interesting" ranking. */
    private static final int RANKING_CANDIDATES = 600;
    private static final long STALE_AFTER_MILLIS = 15 * 60_000L;

    private final AppContainer c;
    private final InterestRanker ranker = new InterestRanker();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<FeedFilter> filter = new MutableLiveData<>();
    private final MediatorLiveData<List<FeedItem>> items = new MediatorLiveData<>();
    private final MediatorLiveData<Integer> hiddenPolitical = new MediatorLiveData<>();
    private LiveData<List<FeedRow>> rowsSource;
    private LiveData<Integer> countSource;
    private volatile int generation;
    private volatile int shownPolitical;
    private volatile int totalPolitical;
    private boolean initialized;

    public FeedViewModel(@NonNull Application app) {
        super(app);
        c = NewsHubApp.container(app);
    }

    public void init(FeedMode mode, Category category) {
        if (initialized) return;
        initialized = true;
        setFilter(c.settings.baseFilter(mode, category));
    }

    public LiveData<FeedFilter> filter() {
        return filter;
    }

    public FeedFilter currentFilter() {
        FeedFilter f = filter.getValue();
        return f != null ? f : FeedFilter.defaults();
    }

    /** null until the first result arrives (the screen shows skeletons meanwhile). */
    public LiveData<List<FeedItem>> items() {
        return items;
    }

    public LiveData<Integer> hiddenPolitical() {
        return hiddenPolitical;
    }

    public void setMode(FeedMode mode, Category category) {
        FeedFilter f = currentFilter();
        if (f.mode == mode && f.category == category) return;
        setFilter(f.withMode(mode, category));
    }

    public void applyFilter(Period period, SortOrder sort, String sourceId) {
        setFilter(currentFilter().withPeriod(period).withSort(sort).withSource(sourceId));
    }

    public void resetFilter() {
        SettingsRepository s = c.settings;
        setFilter(currentFilter().withPeriod(s.defaultPeriod()).withSort(s.sortOrder()).withSource(null));
    }

    /** Picks up changed settings (page size, grouping, politics filter) when the screen resumes. */
    public void applySettings() {
        FeedFilter f = filter.getValue();
        if (f == null) return;
        SettingsRepository s = c.settings;
        FeedFilter updated = f.withSettings(s.pageSize(), s.groupDuplicates(), s.politicsFilterEnabled(), s.politicsMode());
        if (!updated.equals(f)) setFilter(updated);
    }

    /** Re-runs the query so that the time window ("за день", "за неделю") moves to "now". */
    public void reload() {
        FeedFilter f = filter.getValue();
        if (f != null) setFilter(f);
    }

    public void refresh() {
        c.news.refreshAsync(true);
    }

    /** Automatic refresh when the data is older than a few minutes (respects per-source limits). */
    public void refreshIfStale() {
        long last = c.settings.lastRefreshAt();
        if (last > 0 && System.currentTimeMillis() - last > STALE_AFTER_MILLIS && !c.news.isRefreshing()) {
            c.news.refreshAsync(false);
        }
    }

    public void toggleFavorite(FeedItem item) {
        c.news.toggleFavorite(item.row.articleId);
    }

    public void setInterest(Category category, int value) {
        c.settings.setInterest(category, value);
        if (currentFilter().mode == FeedMode.INTERESTING) reload();
    }

    private void setFilter(FeedFilter f) {
        filter.setValue(f);
        if (rowsSource != null) items.removeSource(rowsSource);
        if (countSource != null) hiddenPolitical.removeSource(countSource);
        final int gen = ++generation;
        int limitOverride = f.mode == FeedMode.INTERESTING ? RANKING_CANDIDATES : 0;
        rowsSource = c.news.observeFeed(f, limitOverride);
        items.addSource(rowsSource, rows -> process(f, rows, gen));
        if (f.hidesPolitics() || f.limitsPolitics()) {
            countSource = c.news.observePoliticalCount(f);
            hiddenPolitical.addSource(countSource, total -> {
                totalPolitical = total == null ? 0 : total;
                publishHidden();
            });
        } else {
            countSource = null;
            totalPolitical = 0;
            hiddenPolitical.setValue(0);
        }
    }

    private void publishHidden() {
        hiddenPolitical.setValue(Math.max(0, totalPolitical - shownPolitical));
    }

    private void process(FeedFilter f, List<FeedRow> rows, int gen) {
        if (rows == null || worker.isShutdown()) return;
        worker.execute(() -> {
            List<FeedItem> out;
            if (f.mode == FeedMode.INTERESTING) {
                out = rank(f, rows);
            } else {
                out = new ArrayList<>(rows.size());
                for (FeedRow r : rows) out.add(new FeedItem(r, null, f.groupDuplicates));
            }
            if (f.limitsPolitics()) {
                out = PoliticsLimiter.cap(out, it -> it.row.political, PoliticsLimiter.DEFAULT_SHARE);
            }
            int political = 0;
            for (FeedItem it : out) {
                if (it.row.political) political++;
            }
            if (gen != generation) return;
            shownPolitical = political;
            items.postValue(out);
            hiddenPolitical.postValue(Math.max(0, totalPolitical - political));
        });
    }

    private List<FeedItem> rank(FeedFilter f, List<FeedRow> rows) {
        long now = System.currentTimeMillis();
        InterestRanker.Profile profile = c.news.buildProfile(now);
        List<InterestRanker.Candidate> candidates = new ArrayList<>(rows.size());
        Map<Long, FeedRow> byId = new HashMap<>();
        for (FeedRow r : rows) {
            long id = f.groupDuplicates ? r.clusterId : r.articleId;
            byId.put(id, r);
            candidates.add(new InterestRanker.Candidate(id, r.sourceId,
                    Category.fromKey(r.primaryCategory, Category.WORLD), r.sourceCount,
                    r.firstPublishedAt > 0 ? r.firstPublishedAt : r.publishedAt, r.political, r.isRead));
        }
        List<FeedItem> out = new ArrayList<>();
        for (InterestRanker.Ranked ranked : ranker.rank(candidates, profile, now, f.limit)) {
            FeedRow r = byId.get(ranked.candidate.id);
            if (r != null) out.add(new FeedItem(r, ranked.reasons, f.groupDuplicates));
        }
        return out;
    }

    @Override
    protected void onCleared() {
        worker.shutdownNow();
    }
}
