package com.newshub.app.data.repo;

import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.newshub.app.data.db.dao.SettingsDao;
import com.newshub.app.data.db.entity.SettingEntity;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.classify.PoliticsRules;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.PoliticsMode;
import com.newshub.app.domain.feed.SortOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * User settings stored in Room (table "settings") with an in-memory cache. Reads are served from
 * the cache; writes update the cache immediately and are persisted on a background thread.
 */
public final class SettingsRepository {

    public static final String THEME = "theme";
    public static final String TEXT_SCALE = "text_scale";
    public static final String DEFAULT_MODE = "default_mode";
    public static final String PERIOD = "default_period";
    public static final String SORT = "sort";
    public static final String PAGE_SIZE = "page_size";
    public static final String GROUP_DUPLICATES = "group_duplicates";
    public static final String IMAGES = "images";
    public static final String POLITICS_FILTER = "politics_filter";
    public static final String POLITICS_MODE = "politics_mode";
    public static final String POLITICS_SENSITIVITY = "politics_sensitivity";
    public static final String POLITICS_GROUPS = "politics_groups";
    public static final String POLITICS_BLOCK = "politics_block_words";
    public static final String POLITICS_ALLOW = "politics_allow_words";
    public static final String USE_HISTORY = "use_read_history";
    public static final String REFRESH_INTERVAL = "refresh_interval";
    public static final String WIFI_ONLY = "wifi_only";
    public static final String RETENTION = "retention_days";
    public static final String NOTIFICATIONS = "notifications";
    public static final String NOTIFY_IMPORTANT = "notify_important_only";
    public static final String TELEGRAM = "telegram_enabled";
    public static final String DEMO = "dev_demo_data";
    public static final String LAST_REFRESH = "last_refresh_at";
    public static final String LAST_CLEANUP = "last_cleanup_at";
    public static final String INTEREST_PREFIX = "interest_";

    /** Keys whose change requires re-evaluating the politics flag of stored publications. */
    public static final Set<String> POLITICS_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            POLITICS_SENSITIVITY, POLITICS_GROUPS, POLITICS_BLOCK, POLITICS_ALLOW)));

    private static final String SET_SEPARATOR = "|";
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put(THEME, "dark");
        DEFAULTS.put(TEXT_SCALE, "normal");
        DEFAULTS.put(DEFAULT_MODE, FeedMode.ALL.key);
        DEFAULTS.put(PERIOD, Period.WEEK.key);
        DEFAULTS.put(SORT, SortOrder.NEWEST.key);
        DEFAULTS.put(PAGE_SIZE, "100");
        DEFAULTS.put(GROUP_DUPLICATES, "true");
        DEFAULTS.put(IMAGES, "always");
        DEFAULTS.put(POLITICS_FILTER, "true");
        DEFAULTS.put(POLITICS_MODE, PoliticsMode.LIMIT.key);
        DEFAULTS.put(POLITICS_SENSITIVITY, PoliticsRules.Sensitivity.NORMAL.key);
        DEFAULTS.put(POLITICS_GROUPS, "domestic|geo|military");
        DEFAULTS.put(POLITICS_BLOCK, "");
        DEFAULTS.put(POLITICS_ALLOW, "");
        DEFAULTS.put(USE_HISTORY, "true");
        DEFAULTS.put(REFRESH_INTERVAL, "60");
        DEFAULTS.put(WIFI_ONLY, "false");
        DEFAULTS.put(RETENTION, "30");
        DEFAULTS.put(NOTIFICATIONS, "false");
        DEFAULTS.put(NOTIFY_IMPORTANT, "true");
        DEFAULTS.put(TELEGRAM, "true");
        DEFAULTS.put(DEMO, "false");
        DEFAULTS.put(LAST_REFRESH, "0");
        DEFAULTS.put(LAST_CLEANUP, "0");
    }

    public interface Listener {
        void onSettingChanged(String key);
    }

    private final SettingsDao dao;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final MutableLiveData<Integer> version = new MutableLiveData<>(0);
    private volatile boolean loaded;
    private int versionCounter;

    public SettingsRepository(SettingsDao dao) {
        this.dao = dao;
    }

    /** Loads all settings once. Safe to call from any thread. */
    public void ensureLoaded() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            List<SettingEntity> all;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Room forbids queries on the main thread; the first read waits for a tiny query,
                // like SharedPreferences does on first access.
                try {
                    Future<List<SettingEntity>> f = writer.submit(dao::getAll);
                    all = f.get();
                } catch (Exception e) {
                    all = Collections.emptyList();
                }
            } else {
                all = dao.getAll();
            }
            for (SettingEntity s : all) cache.put(s.settingKey, s.settingValue);
            loaded = true;
        }
    }

    /** Blocks until queued writes reached the database (tests, process shutdown paths). */
    public void awaitWrites() {
        try {
            writer.submit(() -> { }).get();
        } catch (Exception ignored) {
            // nothing to wait for
        }
    }

    public LiveData<Integer> changes() {
        return version;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ---- raw access ----------------------------------------------------------------------------

    public String getString(String key) {
        ensureLoaded();
        String v = cache.get(key);
        if (v != null) return v;
        String d = DEFAULTS.get(key);
        return d != null ? d : "";
    }

    public String getString(String key, String fallback) {
        ensureLoaded();
        String v = cache.get(key);
        if (v != null) return v;
        String d = DEFAULTS.get(key);
        return d != null ? d : fallback;
    }

    public void putString(String key, String value) {
        ensureLoaded();
        String v = value == null ? "" : value;
        String old = cache.put(key, v);
        if (v.equals(old)) return;
        writer.execute(() -> dao.put(new SettingEntity(key, v)));
        notifyChanged(key);
    }

    public boolean getBoolean(String key, boolean fallback) {
        String v = getString(key, null);
        if (v == null || v.isEmpty()) return fallback;
        return Boolean.parseBoolean(v);
    }

    public void putBoolean(String key, boolean value) {
        putString(key, Boolean.toString(value));
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(getString(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public long getLong(String key, long fallback) {
        try {
            return Long.parseLong(getString(key, Long.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void putLong(String key, long value) {
        ensureLoaded();
        String v = Long.toString(value);
        cache.put(key, v);
        writer.execute(() -> dao.put(new SettingEntity(key, v)));
        // bookkeeping values do not notify listeners
    }

    public Set<String> getStringSet(String key, Set<String> fallback) {
        String v = getString(key, null);
        if (v == null) return fallback;
        Set<String> out = new LinkedHashSet<>();
        if (v.isEmpty()) return out;
        for (String part : v.split("\\" + SET_SEPARATOR)) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    public void putStringSet(String key, Set<String> values) {
        putString(key, values == null ? "" : String.join(SET_SEPARATOR, values));
    }

    private void notifyChanged(String key) {
        synchronized (version) {
            versionCounter++;
            version.postValue(versionCounter);
        }
        for (Listener l : listeners) l.onSettingChanged(key);
    }

    // ---- typed accessors -----------------------------------------------------------------------

    public String theme() {
        return getString(THEME);
    }

    public float textScale() {
        switch (getString(TEXT_SCALE)) {
            case "small":
                return 0.9f;
            case "large":
                return 1.15f;
            case "xlarge":
                return 1.3f;
            default:
                return 1.0f;
        }
    }

    public FeedMode defaultMode() {
        return FeedMode.fromKey(getString(DEFAULT_MODE), FeedMode.ALL);
    }

    public Period defaultPeriod() {
        return Period.fromKey(getString(PERIOD), Period.WEEK);
    }

    public SortOrder sortOrder() {
        return SortOrder.fromKey(getString(SORT), SortOrder.NEWEST);
    }

    public int pageSize() {
        return Math.max(20, Math.min(500, getInt(PAGE_SIZE, 100)));
    }

    public boolean groupDuplicates() {
        return getBoolean(GROUP_DUPLICATES, true);
    }

    /** "always", "wifi" or "never". */
    public String imagePolicy() {
        return getString(IMAGES);
    }

    public boolean politicsFilterEnabled() {
        return getBoolean(POLITICS_FILTER, true);
    }

    public PoliticsMode politicsMode() {
        return PoliticsMode.fromKey(getString(POLITICS_MODE), PoliticsMode.LIMIT);
    }

    public PoliticsRules politicsRules() {
        Set<PoliticsRules.Group> groups = EnumSet.noneOf(PoliticsRules.Group.class);
        for (String key : getStringSet(POLITICS_GROUPS, Collections.emptySet())) {
            PoliticsRules.Group g = PoliticsRules.Group.fromKey(key);
            if (g != null) groups.add(g);
        }
        return new PoliticsRules(groups, PoliticsRules.Sensitivity.fromKey(getString(POLITICS_SENSITIVITY)),
                getString(POLITICS_BLOCK), getString(POLITICS_ALLOW));
    }

    /** -1 = less, 0 = neutral, 1 = more. */
    public int interest(Category c) {
        return Math.max(-1, Math.min(1, getInt(INTEREST_PREFIX + c.key, 0)));
    }

    public void setInterest(Category c, int value) {
        putString(INTEREST_PREFIX + c.key, Integer.toString(Math.max(-1, Math.min(1, value))));
    }

    public void resetInterests() {
        for (Category c : Category.values()) putString(INTEREST_PREFIX + c.key, "0");
    }

    public boolean useReadHistory() {
        return getBoolean(USE_HISTORY, true);
    }

    /** Minutes, 0 = automatic refresh off. */
    public int refreshIntervalMinutes() {
        return Math.max(0, getInt(REFRESH_INTERVAL, 60));
    }

    public boolean wifiOnly() {
        return getBoolean(WIFI_ONLY, false);
    }

    public int retentionDays() {
        return Math.max(1, getInt(RETENTION, 30));
    }

    public boolean notificationsEnabled() {
        return getBoolean(NOTIFICATIONS, false);
    }

    public boolean notifyImportantOnly() {
        return getBoolean(NOTIFY_IMPORTANT, true);
    }

    public boolean telegramEnabled() {
        return getBoolean(TELEGRAM, true);
    }

    public boolean demoEnabled() {
        return getBoolean(DEMO, false);
    }

    public long lastRefreshAt() {
        return getLong(LAST_REFRESH, 0);
    }

    /** Filter for a feed screen built from the user's defaults. */
    public FeedFilter baseFilter(FeedMode mode, Category category) {
        return new FeedFilter(mode, category, defaultPeriod(), sortOrder(), null, null, pageSize(), groupDuplicates(),
                politicsFilterEnabled(), politicsMode());
    }
}
