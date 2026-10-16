package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.repo.NewsRepository;
import com.newshub.app.data.repo.RefreshSummary;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.data.source.ProviderRegistry;
import com.newshub.app.data.source.ProviderResult;
import com.newshub.app.data.source.SourceProvider;
import com.newshub.app.domain.SourceType;
import com.newshub.app.domain.model.ParsedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 34, application = Application.class)
public class RefreshPipelineTest {

    private NewsDatabase db;
    private NewsRepository repo;
    private final Map<String, ProviderResult> responses = new HashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    /** Provider that answers from a map instead of the network. */
    private final SourceProvider fake = new SourceProvider() {
        @Override
        public SourceType type() {
            return SourceType.RSS;
        }

        @Override
        public ProviderResult fetch(SourceEntity source, boolean conditional, long now) {
            calls.incrementAndGet();
            ProviderResult r = responses.get(source.id);
            return r != null ? r : ProviderResult.error(ProviderResult.ErrorKind.NETWORK, "нет ответа");
        }
    };

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, NewsDatabase.class).allowMainThreadQueries().build();
        SettingsRepository settings = new SettingsRepository(db.settings());
        repo = new NewsRepository(db, new ProviderRegistry().register(fake), settings, new DirectExecutor());
        db.sources().upsert(source("good"));
        db.sources().upsert(source("bad"));
    }

    @After
    public void tearDown() {
        db.close();
    }

    private static SourceEntity source(String id) {
        SourceEntity s = new SourceEntity();
        s.id = id;
        s.name = id;
        s.type = "RSS";
        s.address = "https://" + id + ".example/rss";
        s.category = "tech";
        s.enabled = true;
        return s;
    }

    private static List<ParsedItem> items(String... titles) {
        java.util.ArrayList<ParsedItem> out = new java.util.ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            ParsedItem it = new ParsedItem();
            it.guid = "g" + i;
            it.title = titles[i];
            it.link = "https://good.example/" + i;
            it.publishedAt = System.currentTimeMillis() - i * 60_000L;
            out.add(it);
        }
        return out;
    }

    @Test
    public void failingSourceDoesNotBlockOthers() {
        responses.put("good", ProviderResult.ok(items("Первая новость о процессорах", "Вторая новость о смартфонах"),
                "Good", "\"etag\"", null, null));
        responses.put("bad", ProviderResult.error(ProviderResult.ErrorKind.TIMEOUT, "тайм-аут"));

        RefreshSummary summary = repo.refreshBlocking(true);
        assertEquals(2, summary.sourcesChecked);
        assertEquals(1, summary.sourcesOk);
        assertEquals(1, summary.sourcesFailed);
        assertEquals(2, summary.newArticles);

        SourceEntity good = db.sources().get("good");
        assertNull(good.lastErrorKind);
        assertEquals("\"etag\"", good.etag);
        assertTrue(good.lastSuccessAt > 0);

        SourceEntity bad = db.sources().get("bad");
        assertEquals("TIMEOUT", bad.lastErrorKind);
        assertEquals(1, bad.consecutiveFailures);
        assertTrue(bad.nextAllowedFetchAt > System.currentTimeMillis());
    }

    @Test
    public void automaticRefreshRespectsBackoffAndMinimumInterval() {
        responses.put("good", ProviderResult.ok(items("Новость о чипах"), null, null, null, null));
        repo.refreshBlocking(true);
        int afterFirst = calls.get();
        RefreshSummary auto = repo.refreshBlocking(false);
        assertEquals(afterFirst, calls.get());
        assertEquals(0, auto.sourcesChecked);
    }

    @Test
    public void rateLimitIsRespectedEvenOnManualRefresh() {
        responses.put("bad", ProviderResult.rateLimited("429", 60 * 60_000L));
        responses.put("good", ProviderResult.notModified(null, null));
        repo.refreshBlocking(true);
        int afterFirst = calls.get();
        repo.refreshBlocking(true);
        // "good" is fetched again, "bad" waits for its Retry-After
        assertEquals(afterFirst + 1, calls.get());
    }

    @Test
    public void politicsOverrideAppliesToTheWholeStory() {
        responses.put("good", ProviderResult.ok(items("Госдума приняла законопроект о криптовалюте"), null, null, null, null));
        repo.refreshBlocking(true);
        ArticleEntity a = db.articles().page(0, 10).get(0);
        assertTrue(a.political);
        repo.setPoliticsOverride(a.clusterId, -1);
        ArticleEntity after = db.articles().get(a.id);
        assertEquals(-1, after.politicsOverride);
        assertTrue(!after.political);
        assertTrue(!db.clusters().get(a.clusterId).political);
        assertEquals(1, db.articles().count());
        assertEquals(Collections.singletonList(a.clusterId), db.articles().clusterIds());
    }
}
