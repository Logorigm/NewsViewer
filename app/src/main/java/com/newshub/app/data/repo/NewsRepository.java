package com.newshub.app.data.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.newshub.app.data.db.FeedQueryBuilder;
import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;
import com.newshub.app.data.db.entity.FavoriteEntity;
import com.newshub.app.data.db.entity.HistoryEntity;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.CategoryCount;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.data.source.ProviderRegistry;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.data.source.SourceProvider;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.classify.PoliticsResult;
import com.newshub.app.domain.classify.TopicClassifier;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.rank.InterestRanker;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Publications, stories, favorites and history. Owns the refresh pipeline:
 * providers (in parallel) → ingest (serialized: dedup + classification) → source status.
 */
public final class NewsRepository {

    /** Automatic refreshes skip a source fetched less than this long ago. */
    public static final long MIN_AUTO_INTERVAL_MILLIS = 10 * 60_000L;
    private static final long PER_SOURCE_TIMEOUT_SECONDS = 90;
    private static final long MAX_BACKOFF_MILLIS = 6 * 3_600_000L;
    private static final long MIN_RATE_LIMIT_MILLIS = 15 * 60_000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 12 * 3_600_000L;
    private static final int FETCH_THREADS = 4;

    private final NewsDatabase db;
    private final ProviderRegistry registry;
    private final SettingsRepository settings;
    private final ExecutorService io;
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(FETCH_THREADS);
    private final ArticleIngestor ingestor;
    private final MutableLiveData<RefreshState> refreshState = new MutableLiveData<>(RefreshState.idle(null));
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final Object maintenanceLock = new Object();
    private volatile RefreshSummary lastSummary;

    public NewsRepository(NewsDatabase db, ProviderRegistry registry, SettingsRepository settings, ExecutorService io) {
        this.db = db;
        this.registry = registry;
        this.settings = settings;
        this.io = io;
        this.ingestor = new ArticleIngestor(db);
    }

    // ---- refresh -------------------------------------------------------------------------------

    public LiveData<RefreshState> refreshState() {
        return refreshState;
    }

    public boolean isRefreshing() {
        return refreshing.get();
    }

    public void refreshAsync(boolean manual) {
        io.execute(() -> refreshBlocking(manual));
    }

    /** Runs on a background thread. Concurrent calls are ignored while a refresh is running. */
    public RefreshSummary refreshBlocking(boolean manual) {
        long now = System.currentTimeMillis();
        if (!refreshing.compareAndSet(false, true)) return RefreshSummary.skippedRun(now);
        refreshState.postValue(RefreshState.running(lastSummary));
        RefreshSummary summary = new RefreshSummary(now);
        try {
            List<SourceEntity> due = dueSources(manual, now);
            Map<SourceEntity, Future<ProviderResult>> futures = new LinkedHashMap<>();
            for (SourceEntity s : due) {
                futures.put(s, fetchPool.submit(() -> safeFetch(s, now)));
            }
            TopicClassifier classifier = new TopicClassifier(settings.politicsRules());
            long retentionCutoff = now - settings.retentionDays() * 86_400_000L;
            synchronized (maintenanceLock) {
                ArticleIngestor.Session session = ingestor.newSession(now);
                for (Map.Entry<SourceEntity, Future<ProviderResult>> e : futures.entrySet()) {
                    ProviderResult r = await(e.getValue());
                    applyResult(session, e.getKey(), r, classifier, now, retentionCutoff, summary);
                }
            }
            settings.putLong(SettingsRepository.LAST_REFRESH, now);
        } catch (RuntimeException e) {
            summary.sourcesFailed++;
            summary.failedSources.add("Внутренняя ошибка обновления: " + e.getMessage());
        } finally {
            lastSummary = summary;
            refreshing.set(false);
            refreshState.postValue(RefreshState.idle(summary));
        }
        return summary;
    }

    private List<SourceEntity> dueSources(boolean manual, long now) {
        boolean telegram = settings.telegramEnabled();
        List<SourceEntity> due = new ArrayList<>();
        for (SourceEntity s : db.sources().getEnabled()) {
            SourceType type = SourceType.parse(s.type, SourceType.RSS);
            if (type == SourceType.TELEGRAM && !telegram) continue;
            if (registry.get(type) == null) continue;
            if (s.address.trim().isEmpty()) continue;
            boolean rateLimited = ProviderResult.ErrorKind.RATE_LIMITED.name().equals(s.lastErrorKind);
            if (s.nextAllowedFetchAt > now && (!manual || rateLimited)) continue;
            if (!manual && now - s.lastFetchAt < MIN_AUTO_INTERVAL_MILLIS) continue;
            due.add(s);
        }
        return due;
    }

    private ProviderResult safeFetch(SourceEntity s, long now) {
        try {
            SourceProvider p = registry.get(SourceType.parse(s.type, SourceType.RSS));
            if (p == null) return ProviderResult.error(ProviderResult.ErrorKind.UNSUPPORTED, "Тип источника не поддерживается");
            return p.fetch(s, true, now);
        } catch (RuntimeException e) {
            return ProviderResult.error(ProviderResult.ErrorKind.PARSE, "Ошибка обработки: " + e.getMessage());
        }
    }

    private static ProviderResult await(Future<ProviderResult> f) {
        try {
            return f.get(PER_SOURCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            return ProviderResult.error(ProviderResult.ErrorKind.TIMEOUT, "Источник отвечает слишком долго");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProviderResult.error(ProviderResult.ErrorKind.NETWORK, "Обновление прервано");
        } catch (Exception e) {
            return ProviderResult.error(ProviderResult.ErrorKind.NETWORK, String.valueOf(e.getMessage()));
        }
    }

    private void applyResult(ArticleIngestor.Session session, SourceEntity fetched, ProviderResult r,
                             TopicClassifier classifier, long now, long retentionCutoff, RefreshSummary summary) {
        SourceEntity s = db.sources().get(fetched.id);
        if (s == null) return; // removed while fetching
        summary.sourcesChecked++;
        s.lastFetchAt = now;
        if (r.status == ProviderResult.Status.ERROR) {
            s.consecutiveFailures++;
            s.lastError = r.message;
            s.lastErrorKind = r.errorKind == null ? null : r.errorKind.name();
            long backoff = Math.min(MAX_BACKOFF_MILLIS, 5 * 60_000L * (1L << Math.min(10, s.consecutiveFailures - 1)));
            if (r.errorKind == ProviderResult.ErrorKind.RATE_LIMITED) {
                backoff = Math.max(MIN_RATE_LIMIT_MILLIS, r.retryAfterMillis);
            }
            s.nextAllowedFetchAt = now + backoff;
            if (r.resetDiscovery) s.discoveredFeedUrl = null;
            db.sources().update(s);
            summary.sourcesFailed++;
            summary.failedSources.add(s.name);
            if (r.errorKind == ProviderResult.ErrorKind.NETWORK || r.errorKind == ProviderResult.ErrorKind.TIMEOUT) {
                summary.networkFailures++;
            }
            return;
        }
        s.lastSuccessAt = now;
        s.lastError = null;
        s.lastErrorKind = null;
        s.consecutiveFailures = 0;
        s.nextAllowedFetchAt = 0;
        if (r.etag != null) s.etag = r.etag;
        if (r.lastModified != null) s.lastModified = r.lastModified;
        if (r.status == ProviderResult.Status.NOT_MODIFIED) {
            db.sources().update(s);
            summary.sourcesNotModified++;
            return;
        }
        if (r.discoveredFeedUrl != null) s.discoveredFeedUrl = r.discoveredFeedUrl;
        s.lastItemCount = r.items.size();
        db.sources().update(s);
        ArticleIngestor.Result ir = session.ingest(s, r.items, classifier, now, retentionCutoff);
        summary.sourcesOk++;
        summary.newArticles += ir.inserted;
        summary.newStories += ir.newStories;
        summary.importantClusterIds.addAll(ir.becameImportant);
    }

    // ---- maintenance ---------------------------------------------------------------------------

    /** Deletes publications older than the retention period (favorites are always kept). */
    public void cleanupIfDue(long now) {
        long last = settings.getLong(SettingsRepository.LAST_CLEANUP, 0);
        if (now - last < CLEANUP_INTERVAL_MILLIS) return;
        synchronized (maintenanceLock) {
            long cutoff = now - settings.retentionDays() * 86_400_000L;
            db.runInTransaction(() -> {
                db.articles().deleteOlderThan(cutoff);
                db.history().deleteOrphans();
                db.clusters().deleteOrphans();
            });
        }
        settings.putLong(SettingsRepository.LAST_CLEANUP, now);
    }

    /** Re-evaluates the politics flag of every stored publication after the rules changed. */
    public void reclassifyPoliticsAsync() {
        io.execute(this::reclassifyPoliticsBlocking);
    }

    public void reclassifyPoliticsBlocking() {
        TopicClassifier classifier = new TopicClassifier(settings.politicsRules());
        synchronized (maintenanceLock) {
            long after = 0;
            while (true) {
                List<ArticleEntity> page = db.articles().page(after, 500);
                if (page.isEmpty()) break;
                for (ArticleEntity a : page) {
                    PoliticsResult p = classifier.politicsRules().evaluate(a.title, a.summary);
                    a.politicsScore = p.score;
                    a.politicsMatches = p.matches.isEmpty() ? null : String.join(", ", p.matches);
                    a.political = ArticleIngestor.effectivePolitical(p.political, a.politicsOverride);
                    after = a.id;
                }
                db.articles().updateAll(page);
            }
            List<Long> ids = db.articles().clusterIds();
            db.runInTransaction(() -> {
                for (long id : ids) ArticleIngestor.recomputeCluster(db, id);
            });
        }
    }

    /** User override for a whole story: 1 = political, -1 = not political, 0 = automatic. */
    public void setPoliticsOverride(long clusterId, int value) {
        io.execute(() -> {
            TopicClassifier classifier = new TopicClassifier(settings.politicsRules());
            synchronized (maintenanceLock) {
                db.runInTransaction(() -> {
                    db.articles().setPoliticsOverride(clusterId, value);
                    List<ArticleEntity> articles = db.articles().byCluster(clusterId);
                    for (ArticleEntity a : articles) {
                        PoliticsResult p = classifier.politicsRules().evaluate(a.title, a.summary);
                        a.political = ArticleIngestor.effectivePolitical(p.political, value);
                    }
                    db.articles().updateAll(articles);
                    ArticleIngestor.recomputeCluster(db, clusterId);
                });
            }
        });
    }

    public void clearCache(Runnable done) {
        io.execute(() -> {
            synchronized (maintenanceLock) {
                db.runInTransaction(() -> {
                    db.articles().deleteAllExceptFavorites();
                    db.history().deleteOrphans();
                    db.clusters().deleteOrphans();
                    for (long id : db.articles().clusterIds()) ArticleIngestor.recomputeCluster(db, id);
                });
            }
            if (done != null) done.run();
        });
    }

    // ---- queries -------------------------------------------------------------------------------

    public LiveData<List<FeedRow>> observeFeed(FeedFilter filter, int limitOverride) {
        return db.feed().observe(NewsDatabase.toQuery(FeedQueryBuilder.rows(filter, System.currentTimeMillis(), limitOverride)));
    }

    public List<FeedRow> queryFeed(FeedFilter filter, int limitOverride) {
        return db.feed().query(NewsDatabase.toQuery(FeedQueryBuilder.rows(filter, System.currentTimeMillis(), limitOverride)));
    }

    public LiveData<Integer> observePoliticalCount(FeedFilter filter) {
        return db.feed().observeCount(NewsDatabase.toQuery(FeedQueryBuilder.politicalCount(filter, System.currentTimeMillis())));
    }

    public LiveData<List<Integer>> observeCategoryMasks(long since) {
        return db.clusters().observeMasksSince(since);
    }

    public LiveData<ArticleEntity> observeArticle(long id) {
        return db.articles().observe(id);
    }

    public LiveData<ClusterEntity> observeCluster(long id) {
        return db.clusters().observe(id);
    }

    public LiveData<List<ArticleEntity>> observeClusterArticles(long clusterId) {
        return db.articles().observeByCluster(clusterId);
    }

    public LiveData<List<FeedRow>> observeFavorites() {
        return db.favorites().observeFavorites();
    }

    public LiveData<List<FeedRow>> observeHistory() {
        return db.history().observeHistory();
    }

    public LiveData<Boolean> observeFavorite(long articleId) {
        return db.favorites().observeIsFavorite(articleId);
    }

    public void setFavorite(long articleId, boolean favorite) {
        io.execute(() -> {
            if (favorite) {
                db.favorites().add(new FavoriteEntity(articleId, System.currentTimeMillis()));
            } else {
                db.favorites().remove(articleId);
            }
        });
    }

    public void toggleFavorite(long articleId) {
        io.execute(() -> {
            if (db.favorites().isFavorite(articleId)) {
                db.favorites().remove(articleId);
            } else {
                db.favorites().add(new FavoriteEntity(articleId, System.currentTimeMillis()));
            }
        });
    }

    public void markRead(long articleId) {
        io.execute(() -> db.history().add(new HistoryEntity(articleId, System.currentTimeMillis())));
    }

    public void clearHistory(Runnable done) {
        io.execute(() -> {
            db.history().clear();
            if (done != null) done.run();
        });
    }

    /** Reading profile for the "interesting" ranking (computed on a background thread). */
    public InterestRanker.Profile buildProfile(long now) {
        Map<Category, Integer> explicit = new EnumMap<>(Category.class);
        for (Category c : Category.assignable()) explicit.put(c, settings.interest(c));
        Map<Category, Double> share = new HashMap<>();
        boolean useHistory = settings.useReadHistory();
        if (useHistory) {
            List<CategoryCount> counts = db.history().readCountsSince(now - 30L * 86_400_000L);
            int total = 0;
            for (CategoryCount c : counts) total += c.count;
            if (total >= 5) {
                for (CategoryCount c : counts) {
                    share.put(Category.fromKey(c.category, Category.WORLD), (double) c.count / total);
                }
            }
        }
        return new InterestRanker.Profile(explicit, share, useHistory);
    }

    public void runInBackground(Runnable r) {
        io.execute(r);
    }

    public <T> void loadInBackground(java.util.concurrent.Callable<T> task, Consumer<T> onResult) {
        io.execute(() -> {
            try {
                onResult.accept(task.call());
            } catch (Exception e) {
                onResult.accept(null);
            }
        });
    }
}
