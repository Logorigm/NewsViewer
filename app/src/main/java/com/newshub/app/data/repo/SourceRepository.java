package com.newshub.app.data.repo;

import androidx.lifecycle.LiveData;

import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.SourceCount;
import com.newshub.app.data.source.CatalogSource;
import com.newshub.app.data.source.ProviderRegistry;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.data.source.SourceCatalogParser;
import com.newshub.app.data.source.SourceProvider;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.VerificationLevel;
import com.newshub.app.domain.parse.TelegramPreviewParser;
import com.newshub.app.domain.text.UrlNormalizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Configured sources: built-in catalog seeding, user edits, validation and test fetches. */
public final class SourceRepository {

    private final NewsDatabase db;
    private final ProviderRegistry registry;
    private final ExecutorService io;

    public SourceRepository(NewsDatabase db, ProviderRegistry registry, ExecutorService io) {
        this.db = db;
        this.registry = registry;
        this.io = io;
    }

    /** Result of validating a user-entered source. */
    public static final class Validation {
        public final boolean ok;
        public final String normalizedAddress;
        public final String error;

        private Validation(boolean ok, String normalizedAddress, String error) {
            this.ok = ok;
            this.normalizedAddress = normalizedAddress;
            this.error = error;
        }

        static Validation ok(String address) {
            return new Validation(true, address, null);
        }

        static Validation fail(String error) {
            return new Validation(false, null, error);
        }
    }

    // ---- catalog -------------------------------------------------------------------------------

    /** Adds catalog sources that are not in the database yet. Never overrides user changes. */
    public int seedBuiltIns(String catalogJson) {
        List<CatalogSource> catalog;
        try {
            catalog = SourceCatalogParser.parse(catalogJson);
        } catch (Exception e) {
            return 0;
        }
        Set<String> known = new HashSet<>();
        for (SourceEntity s : db.sources().getAllIncludingDeleted()) known.add(s.id);
        long now = System.currentTimeMillis();
        List<SourceEntity> toInsert = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            CatalogSource c = catalog.get(i);
            if (!known.contains(c.id)) toInsert.add(c.toEntity(i, now));
        }
        if (!toInsert.isEmpty()) db.sources().insertAll(toInsert);
        return toInsert.size();
    }

    /** Restores every built-in source to its catalog state (user-added sources are kept). */
    public void resetBuiltIns(String catalogJson, Runnable done) {
        io.execute(() -> {
            try {
                List<CatalogSource> catalog = SourceCatalogParser.parse(catalogJson);
                long now = System.currentTimeMillis();
                db.runInTransaction(() -> {
                    for (int i = 0; i < catalog.size(); i++) {
                        db.sources().upsert(catalog.get(i).toEntity(i, now));
                    }
                });
            } catch (Exception ignored) {
                // catalog is validated by unit tests; nothing to do at runtime
            }
            if (done != null) done.run();
        });
    }

    // ---- observation ---------------------------------------------------------------------------

    public LiveData<List<SourceEntity>> observeSources() {
        return db.sources().observeAll();
    }

    public LiveData<List<SourceCount>> observeArticleCounts() {
        return db.sources().observeArticleCounts();
    }

    public LiveData<SourceEntity> observe(String id) {
        return db.sources().observe(id);
    }

    // ---- editing -------------------------------------------------------------------------------

    /** Checks the address format and that the same source is not configured twice. Background thread. */
    public Validation validate(SourceType type, String rawAddress, String excludeId) {
        String address;
        if (type == SourceType.TELEGRAM) {
            String handle = TelegramPreviewParser.normalizeHandle(rawAddress);
            if (handle == null) {
                return Validation.fail("Нужно публичное имя канала: 5–32 латинские буквы, цифры или _, например @channel");
            }
            address = handle;
        } else {
            address = UrlNormalizer.normalizeUserUrl(rawAddress);
            if (!UrlNormalizer.isHttpsUrl(address)) {
                return Validation.fail("Введите адрес сайта или RSS-ленты, например https://example.ru/rss");
            }
        }
        String key = SourceCatalogParser.addressKey(type.name(), address);
        for (SourceEntity s : db.sources().getAll()) {
            if (s.id.equals(excludeId) || s.address.isEmpty()) continue;
            boolean same = SourceCatalogParser.addressKey(s.type, s.address).equals(key)
                    || (s.discoveredFeedUrl != null
                    && SourceCatalogParser.addressKey(s.type, s.discoveredFeedUrl).equals(key));
            if (same) return Validation.fail("Этот источник уже добавлен: «" + s.name + "»");
        }
        return Validation.ok(address);
    }

    /**
     * Saves a new or edited source. {@code callback} receives null on success or an error text.
     * Changing the address resets the fetch state so the source is checked from scratch.
     */
    public void save(String existingId, String name, SourceType type, String rawAddress, String category,
                     Consumer<String> callback) {
        io.execute(() -> {
            Validation v = validate(type, rawAddress, existingId);
            if (!v.ok) {
                callback.accept(v.error);
                return;
            }
            String cleanName = name == null ? "" : name.trim();
            if (cleanName.isEmpty()) cleanName = v.normalizedAddress;
            SourceEntity s = existingId == null ? null : db.sources().get(existingId);
            if (s == null) {
                s = new SourceEntity();
                s.id = "user_" + UUID.randomUUID().toString().substring(0, 8);
                s.builtIn = false;
                s.verification = VerificationLevel.UNVERIFIED.name();
                s.note = "Добавлен вами";
                s.sortOrder = db.sources().maxSortOrder() + 1;
                s.createdAt = System.currentTimeMillis();
                s.enabled = true;
            }
            boolean addressChanged = !v.normalizedAddress.equals(s.address) || !type.name().equals(s.type);
            boolean renamed = !cleanName.equals(s.name);
            s.name = cleanName;
            s.type = type.name();
            s.address = v.normalizedAddress;
            s.category = category;
            if (addressChanged) {
                s.discoveredFeedUrl = null;
                s.etag = null;
                s.lastModified = null;
                s.lastError = null;
                s.lastErrorKind = null;
                s.consecutiveFailures = 0;
                s.nextAllowedFetchAt = 0;
                s.lastFetchAt = 0;
                s.enabled = true;
            }
            db.sources().upsert(s);
            if (renamed) db.articles().renameSource(s.id, s.name);
            callback.accept(null);
        });
    }

    public void setEnabled(String id, boolean enabled) {
        io.execute(() -> db.sources().setEnabled(id, enabled));
    }

    /**
     * Removes a source and its stored publications (favorites are kept). A built-in source is
     * only marked as deleted so that it does not come back on the next start.
     */
    public void delete(String id, Runnable done) {
        io.execute(() -> {
            db.runInTransaction(() -> {
                SourceEntity s = db.sources().get(id);
                if (s == null) return;
                List<Long> clusters = db.articles().clusterIdsOfSource(id);
                if (s.builtIn) {
                    s.deleted = true;
                    s.enabled = false;
                    db.sources().update(s);
                } else {
                    db.sources().delete(id);
                }
                db.articles().deleteBySource(id);
                db.history().deleteOrphans();
                for (long c : clusters) ArticleIngestor.recomputeCluster(db, c);
            });
            if (done != null) done.run();
        });
    }

    /** Fetches a source once without saving anything (the "Проверить" button). Background thread. */
    public ProviderResult test(SourceType type, String address) {
        SourceProvider provider = registry.get(type);
        if (provider == null) {
            return ProviderResult.error(ProviderResult.ErrorKind.UNSUPPORTED, "Тип источника не поддерживается");
        }
        SourceEntity probe = new SourceEntity();
        probe.id = "probe";
        probe.name = "probe";
        probe.type = type.name();
        probe.address = address;
        try {
            return provider.fetch(probe, false, System.currentTimeMillis());
        } catch (RuntimeException e) {
            return ProviderResult.error(ProviderResult.ErrorKind.PARSE, String.valueOf(e.getMessage()));
        }
    }

    public void testAsync(SourceType type, String rawAddress, String excludeId,
                          Consumer<String> onValidationError, Consumer<ProviderResult> onResult) {
        io.execute(() -> {
            Validation v = validate(type, rawAddress, excludeId);
            if (!v.ok) {
                onValidationError.accept(v.error);
                return;
            }
            onResult.accept(test(type, v.normalizedAddress));
        });
    }
}
