package com.newshub.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.newshub.app.data.db.FeedQueryBuilder;
import com.newshub.app.domain.Category;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.domain.feed.PoliticsMode;

import org.junit.Test;

import java.util.Arrays;

public class FeedQueryBuilderTest {

    private static final long NOW = 1_792_150_000_000L;

    @Test
    public void hidePoliticsAddsCondition() {
        FeedFilter f = FeedFilter.defaults().withSettings(100, true, true, PoliticsMode.HIDE);
        FeedQueryBuilder.Built b = FeedQueryBuilder.rows(f, NOW, 0);
        assertTrue(b.sql.contains("c.political = 0"));
        assertEquals(100, b.args[b.args.length - 1]);
    }

    @Test
    public void limitModeKeepsWidelyReportedPolitics() {
        FeedQueryBuilder.Built b = FeedQueryBuilder.rows(FeedFilter.defaults(), NOW, 0);
        assertTrue(b.sql.contains("(c.political = 0 OR c.sourceCount >= 2)"));
    }

    @Test
    public void filterOffShowsEverything() {
        FeedFilter f = FeedFilter.defaults().withSettings(100, true, false, PoliticsMode.HIDE);
        assertFalse(FeedQueryBuilder.rows(f, NOW, 0).sql.contains("political"
                + " = 0"));
    }

    @Test
    public void categoryPeriodSourceAndSearch() {
        FeedFilter f = FeedFilter.defaults().withMode(FeedMode.CATEGORY, Category.SCIENCE)
                .withPeriod(Period.DAY).withSource("tass").withQuery("Ракеты Союз");
        FeedQueryBuilder.Built b = FeedQueryBuilder.rows(f, NOW, 0);
        assertTrue(b.sql.contains("(c.categoryMask & ?) <> 0"));
        assertTrue(b.sql.contains("x.sourceId = ?"));
        assertTrue(b.sql.contains("q.searchText LIKE ?"));
        assertTrue(Arrays.asList(b.args).contains("tass"));
        assertTrue(Arrays.asList(b.args).contains(Category.SCIENCE.mask()));
        assertTrue(Arrays.asList(b.args).contains(NOW - 86_400_000L));
        assertTrue(Arrays.asList(b.args).contains("%ракет%"));
        assertTrue(Arrays.asList(b.args).contains("%союз%"));
    }

    @Test
    public void ungroupedQueriesArticles() {
        FeedFilter f = FeedFilter.defaults().withSettings(50, false, true, PoliticsMode.HIDE);
        FeedQueryBuilder.Built b = FeedQueryBuilder.rows(f, NOW, 0);
        assertTrue(b.sql.contains("FROM articles a JOIN sources s"));
        assertTrue(b.sql.contains("a.political = 0"));
    }

    @Test
    public void politicalCountSelectsOnlyPolitics() {
        FeedQueryBuilder.Built b = FeedQueryBuilder.politicalCount(FeedFilter.defaults(), NOW);
        assertTrue(b.sql.startsWith("SELECT COUNT(*)"));
        assertTrue(b.sql.contains("c.political = 1"));
        assertFalse(b.sql.contains("LIMIT"));
    }
}
