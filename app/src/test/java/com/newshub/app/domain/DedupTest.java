package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.newshub.app.domain.dedup.ArticleFingerprint;
import com.newshub.app.domain.dedup.DedupDecision.Verdict;
import com.newshub.app.domain.dedup.DedupIndex;
import com.newshub.app.domain.dedup.SimilarityScorer;
import com.newshub.app.domain.dedup.StoryStats;

import org.junit.Test;

import java.util.Arrays;

public class DedupTest {

    private static final long T = 1_792_150_000_000L;
    private static final long HOUR = 3_600_000L;
    private final SimilarityScorer scorer = new SimilarityScorer();

    private static ArticleFingerprint fp(long id, String source, String url, String title, String summary, long t) {
        return ArticleFingerprint.of(id, source, url, title, summary, t);
    }

    @Test
    public void sameEventDifferentWordingIsMerged() {
        ArticleFingerprint a = fp(1, "tass", "https://tass.ru/kosmos/1",
                "Роскосмос успешно запустил ракету «Союз-2.1б» со спутником «Метеор-М»", "", T);
        ArticleFingerprint b = fp(2, "ria", "https://ria.ru/space/2",
                "Ракета «Союз-2.1б» со спутником «Метеор-М» стартовала с Восточного", "", T + HOUR);
        assertEquals(Verdict.SAME, scorer.compare(a, b).verdict);
    }

    @Test
    public void sameLinkIsAlwaysSame() {
        ArticleFingerprint a = fp(1, "a", "https://www.example.org/n/1?utm_source=tg", "Заголовок один", "", T);
        ArticleFingerprint b = fp(2, "b", "https://example.org/n/1", "Совсем другой текст", "", T);
        assertEquals(Verdict.SAME, scorer.compare(a, b).verdict);
    }

    @Test
    public void differentNumbersAreDifferentEvents() {
        ArticleFingerprint a = fp(1, "a", "u1", "Курс доллара на Мосбирже превысил 95 рублей", "", T);
        ArticleFingerprint b = fp(2, "b", "u2", "Курс доллара на Мосбирже превысил 97 рублей", "", T + HOUR);
        assertEquals(Verdict.DIFFERENT, scorer.compare(a, b).verdict);
    }

    @Test
    public void templateHeadlinesWithDifferentSubjectsAreNotMerged() {
        ArticleFingerprint a = fp(1, "a", "u1", "В Москве открылась выставка Айвазовского", "", T);
        ArticleFingerprint b = fp(2, "b", "u2", "В Москве открылась выставка Шагала", "", T);
        assertNotEquals(Verdict.SAME, scorer.compare(a, b).verdict);
    }

    @Test
    public void largeTimeGapIsNeverMerged() {
        ArticleFingerprint a = fp(1, "a", "u1", "Apple представила новый iPhone с увеличенной батареей", "", T);
        ArticleFingerprint b = fp(2, "b", "u2", "Apple представила новый iPhone с увеличенной батареей", "",
                T + 72 * HOUR);
        assertEquals(Verdict.DIFFERENT, scorer.compare(a, b).verdict);
    }

    @Test
    public void unrelatedHeadlinesAreDifferent() {
        ArticleFingerprint a = fp(1, "a", "u1", "Учёные нашли воду на Марсе", "", T);
        ArticleFingerprint b = fp(2, "b", "u2", "Сборная России выиграла чемпионат по хоккею", "", T);
        assertEquals(Verdict.DIFFERENT, scorer.compare(a, b).verdict);
    }

    @Test
    public void lowConfidenceIsNotMerged() {
        ArticleFingerprint a = fp(1, "a", "u1", "Nvidia выпустила драйвер для видеокарт", "", T);
        ArticleFingerprint b = fp(2, "b", "u2", "AMD выпустила драйвер для видеокарт", "", T);
        assertNotEquals(Verdict.SAME, scorer.compare(a, b).verdict);
    }

    @Test
    public void indexFindsStoryAcrossSources() {
        DedupIndex index = new DedupIndex(scorer);
        index.add(fp(1, "tass", "https://tass.ru/1",
                "Роскосмос успешно запустил ракету «Союз-2.1б» со спутником «Метеор-М»", "", T), 100);
        index.add(fp(2, "rbc", "https://rbc.ru/2", "Курс евро опустился ниже 90 рублей", "", T), 200);

        DedupIndex.Match m = index.findSameStory(fp(3, "ria", "https://ria.ru/3",
                "Ракета «Союз-2.1б» со спутником «Метеор-М» стартовала с Восточного", "", T + HOUR));
        assertNotNull(m);
        assertEquals(100, m.clusterId);

        assertNull(index.findSameStory(fp(4, "ria", "https://ria.ru/4",
                "В Петербурге открыли новую станцию метро", "", T)));
    }

    @Test
    public void storyStatsAggregate() {
        StoryStats s = StoryStats.aggregate(Arrays.asList(
                new StoryStats.Member("tass", T, Category.SCIENCE.mask(), "science", 0, false),
                new StoryStats.Member("ria", T + HOUR, Category.SCIENCE.mask() | Category.WORLD.mask(), "science", 2, false),
                new StoryStats.Member("ria", T + 2 * HOUR, Category.WORLD.mask(), "world", 1, false)));
        assertEquals(3, s.articleCount);
        assertEquals(2, s.sourceCount);
        assertEquals(T, s.firstPublishedAt);
        assertEquals(T + 2 * HOUR, s.lastPublishedAt);
        assertEquals("science", s.primaryCategory);
        assertEquals(2, s.politicsScore);
        assertEquals(Category.MAIN.mask() | Category.SCIENCE.mask() | Category.WORLD.mask(), s.categoryMask);
    }

    @Test
    public void singleSourceStoryIsNotMain() {
        StoryStats s = StoryStats.aggregate(Arrays.asList(
                new StoryStats.Member("tass", T, Category.WORLD.mask() | Category.MAIN.mask(), "world", 9, true)));
        assertEquals(0, s.categoryMask & Category.MAIN.mask());
        assertEquals(true, s.political);
    }
}
