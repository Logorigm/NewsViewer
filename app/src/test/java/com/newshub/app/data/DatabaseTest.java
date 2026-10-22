package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.newshub.app.data.db.FeedQueryBuilder;
import com.newshub.app.data.db.NewsDatabase;
import com.newshub.app.data.db.entity.FavoriteEntity;
import com.newshub.app.data.db.entity.HistoryEntity;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.data.repo.ArticleIngestor;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.classify.PoliticsRules;
import com.newshub.app.domain.classify.TopicClassifier;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.PoliticsMode;
import com.newshub.app.domain.model.ParsedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 34, application = Application.class)
public class DatabaseTest {

    private static final long HOUR = 3_600_000L;
    private NewsDatabase db;
    private long now;
    private final TopicClassifier classifier = new TopicClassifier(PoliticsRules.defaults());

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, NewsDatabase.class).allowMainThreadQueries().build();
        now = System.currentTimeMillis();
        db.sources().upsert(source("tass", "ТАСС", "world"));
        db.sources().upsert(source("ria", "РИА Новости", "world"));
        db.sources().upsert(source("habr", "Хабр", "tech"));
    }

    @After
    public void tearDown() {
        db.close();
    }

    private static SourceEntity source(String id, String name, String category) {
        SourceEntity s = new SourceEntity();
        s.id = id;
        s.name = name;
        s.type = "RSS";
        s.address = "https://" + id + ".example/rss";
        s.category = category;
        s.enabled = true;
        return s;
    }

    private ParsedItem item(String guid, String title, String summary, long publishedAt) {
        ParsedItem it = new ParsedItem();
        it.guid = guid;
        it.title = title;
        it.summary = summary;
        it.link = "https://example.org/" + guid;
        it.publishedAt = publishedAt;
        return it;
    }

    private ArticleIngestor.Result ingest(String sourceId, ParsedItem... items) {
        ArticleIngestor ingestor = new ArticleIngestor(db);
        return ingestor.newSession(now).ingest(db.sources().get(sourceId), Arrays.asList(items), classifier, now, 0);
    }

    private List<FeedRow> feed(FeedFilter f) {
        return db.feed().query(NewsDatabase.toQuery(FeedQueryBuilder.rows(f, now, 0)));
    }

    private static FeedFilter all() {
        return FeedFilter.defaults().withPeriod(Period.ALL).withSettings(100, true, false, PoliticsMode.LIMIT);
    }

    @Test
    public void sameStoryFromTwoSourcesIsOneCard() {
        ingest("tass", item("t1", "Роскосмос успешно запустил ракету «Союз-2.1б» со спутником «Метеор-М»", "", now - 2 * HOUR));
        ArticleIngestor.Result r = ingest("ria",
                item("r1", "Ракета «Союз-2.1б» со спутником «Метеор-М» стартовала с Восточного", "", now - HOUR));
        assertEquals(1, r.becameImportant.size());

        List<FeedRow> rows = feed(all());
        assertEquals(1, rows.size());
        FeedRow row = rows.get(0);
        assertEquals(2, row.sourceCount);
        assertEquals(2, row.articleCount);
        assertEquals(now - 2 * HOUR, row.firstPublishedAt);
        assertTrue((row.categoryMask & Category.MAIN.mask()) != 0);
        assertEquals(2, db.articles().byCluster(row.clusterId).size());

        FeedFilter flat = all().withSettings(100, false, false, PoliticsMode.LIMIT);
        assertEquals(2, feed(flat).size());
    }

    @Test
    public void differentEventsStaySeparate() {
        ingest("tass", item("t1", "Курс доллара на Мосбирже превысил 95 рублей", "", now - HOUR));
        ingest("ria", item("r1", "Курс доллара на Мосбирже превысил 97 рублей", "", now));
        assertEquals(2, feed(all()).size());
    }

    @Test
    public void reingestingTheSameFeedAddsNothing() {
        ParsedItem a = item("h1", "Вышла новая версия Linux", "Подробности обновления ядра", now - HOUR);
        assertEquals(1, ingest("habr", a).inserted);
        assertEquals(0, ingest("habr", a).inserted);
        assertEquals(1, db.articles().count());
    }

    @Test
    public void favoritesSurviveRetentionCleanupAndCacheClearing() {
        ingest("habr", item("old", "Старая статья про процессоры", "", now - 40L * 24 * HOUR),
                item("new", "Новая статья про видеокарты", "", now - HOUR));
        FeedRow old = null;
        for (FeedRow r : feed(all())) {
            if (r.title.startsWith("Старая")) old = r;
        }
        db.favorites().add(new FavoriteEntity(old.articleId, now));
        db.articles().deleteOlderThan(now - 30L * 24 * HOUR);
        db.clusters().deleteOrphans();
        assertEquals(2, db.articles().count());

        db.articles().deleteAllExceptFavorites();
        db.clusters().deleteOrphans();
        assertEquals(1, db.articles().count());
        List<FeedRow> favorites = db.favorites().getFavorites();
        assertEquals(1, favorites.size());
        assertTrue(favorites.get(0).isFavorite);
    }

    @Test
    public void historyMarksStoryAsRead() {
        ingest("habr", item("h1", "Статья про Kotlin и Java", "", now - HOUR));
        FeedRow row = feed(all()).get(0);
        assertFalse(row.isRead);
        db.history().add(new HistoryEntity(row.articleId, now));
        assertTrue(feed(all()).get(0).isRead);
    }

    @Test
    public void searchMatchesWordForms() {
        ingest("tass", item("t1", "Роскосмос запустил ракету с Восточного", "Старт прошёл штатно", now - HOUR));
        ingest("habr", item("h1", "Новый фреймворк для веб-разработки", "", now - HOUR));
        assertEquals(1, feed(all().withQuery("ракеты")).size());
        assertEquals(1, feed(all().withQuery("штатно старт")).size());
        assertEquals(0, feed(all().withQuery("футбол")).size());
    }

    @Test
    public void politicsFilterHidesButNeverDeletes() {
        ingest("tass", item("t1", "Госдума приняла законопроект о маркетплейсах", "", now - HOUR));
        ingest("habr", item("h1", "Вышел новый смартфон с быстрой зарядкой", "", now - HOUR));
        FeedFilter hide = all().withSettings(100, true, true, PoliticsMode.HIDE);
        assertEquals(1, feed(hide).size());
        assertEquals(1, feed(hide.withMode(FeedMode.POLITICAL_ONLY, null)).size());
        assertEquals(1, db.feed().count(NewsDatabase.toQuery(FeedQueryBuilder.politicalCount(hide, now))));
        assertEquals(2, db.articles().count());
        assertEquals(2, feed(all()).size());
    }

    @Test
    public void disabledSourceDisappearsFromFeed() {
        ingest("habr", item("h1", "Статья про базы данных", "", now - HOUR));
        assertEquals(1, feed(all()).size());
        db.sources().setEnabled("habr", false);
        assertEquals(0, feed(all()).size());
    }

    @Test
    public void categoryAndPeriodFilters() {
        ingest("tass", item("t1", "Сборная России по хоккею выиграла матч", "", now - HOUR),
                item("t2", "Учёные открыли новую экзопланету с помощью телескопа", "", now - 3L * 24 * HOUR));
        FeedFilter sport = all().withMode(FeedMode.CATEGORY, Category.SPORT);
        assertEquals(1, feed(sport).size());
        FeedFilter science = all().withMode(FeedMode.CATEGORY, Category.SCIENCE);
        assertEquals(1, feed(science).size());
        assertEquals(0, feed(science.withPeriod(Period.DAY)).size());
    }

    @Test
    public void settingsArePersistedInRoom() {
        SettingsRepository settings = new SettingsRepository(db.settings());
        assertEquals("dark", settings.theme());
        settings.putString(SettingsRepository.THEME, "light");
        settings.setInterest(Category.GAMES, 1);
        settings.putStringSet(SettingsRepository.POLITICS_GROUPS, Collections.singleton("geo"));
        settings.awaitWrites();

        SettingsRepository reloaded = new SettingsRepository(db.settings());
        assertEquals("light", reloaded.theme());
        assertEquals(1, reloaded.interest(Category.GAMES));
        assertEquals(1, reloaded.politicsRules().enabledGroups().size());
    }
}
