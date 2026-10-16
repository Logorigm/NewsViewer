package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.newshub.app.domain.rank.InterestRanker;
import com.newshub.app.domain.rank.InterestRanker.Candidate;
import com.newshub.app.domain.rank.InterestRanker.Profile;
import com.newshub.app.domain.rank.InterestRanker.Ranked;
import com.newshub.app.domain.rank.InterestRanker.ReasonType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InterestRankerTest {

    private static final long NOW = 1_792_150_000_000L;
    private static final long HOUR = 3_600_000L;
    private final InterestRanker ranker = new InterestRanker();

    private static Candidate c(long id, String src, Category cat, int sources, long age, boolean political, boolean read) {
        return new Candidate(id, src, cat, sources, NOW - age, political, read);
    }

    private static boolean has(Ranked r, ReasonType t) {
        for (InterestRanker.Reason reason : r.reasons) {
            if (reason.type == t) return true;
        }
        return false;
    }

    @Test
    public void oneSourceCannotFillTheFeed() {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) list.add(c(i, "flood", Category.AI, 1, i * 60_000L, false, false));
        for (int i = 0; i < 10; i++) list.add(c(100 + i, "s" + i, Category.WORLD, 1, 20 * HOUR, false, false));
        List<Ranked> out = ranker.rank(list, Profile.neutral(), NOW, 10);
        Map<String, Integer> perSource = new HashMap<>();
        for (Ranked r : out) perSource.merge(r.candidate.sourceId, 1, Integer::sum);
        assertEquals(10, out.size());
        assertTrue(perSource.get("flood") <= 2);
    }

    @Test
    public void freshBeatsOld() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, "a", Category.TECH, 1, 30 * HOUR, false, false));
        list.add(c(2, "b", Category.TECH, 1, HOUR, false, false));
        List<Ranked> out = ranker.rank(list, Profile.neutral(), NOW, 2);
        assertEquals(2, out.get(0).candidate.id);
        assertTrue(has(out.get(0), ReasonType.FRESH));
    }

    @Test
    public void explicitPreferenceMovesTopicUp() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, "a", Category.SCIENCE, 1, 2 * HOUR, false, false));
        list.add(c(2, "b", Category.SPORT, 1, 2 * HOUR, false, false));
        Map<Category, Integer> explicit = new EnumMap<>(Category.class);
        explicit.put(Category.SPORT, 1);
        explicit.put(Category.SCIENCE, -1);
        List<Ranked> out = ranker.rank(list, new Profile(explicit, null, false), NOW, 2);
        assertEquals(2, out.get(0).candidate.id);
        assertTrue(has(out.get(0), ReasonType.LIKED_TOPIC));
    }

    @Test
    public void multiSourceIsExplainedAndHelps() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, "a", Category.ECONOMY, 1, 3 * HOUR, false, false));
        list.add(c(2, "b", Category.ECONOMY, 4, 3 * HOUR, false, false));
        List<Ranked> out = ranker.rank(list, Profile.neutral(), NOW, 2);
        assertEquals(2, out.get(0).candidate.id);
        assertTrue(has(out.get(0), ReasonType.MULTI_SOURCE));
    }

    @Test
    public void readAndPoliticalItemsGoDown() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, "a", Category.TECH, 1, HOUR, false, true));
        list.add(c(2, "b", Category.TECH, 1, HOUR, true, false));
        list.add(c(3, "c", Category.TECH, 1, 2 * HOUR, false, false));
        List<Ranked> out = ranker.rank(list, Profile.neutral(), NOW, 3);
        assertEquals(3, out.get(0).candidate.id);
    }

    @Test
    public void readingHistoryBoostsTopic() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, "a", Category.HEALTH, 1, 2 * HOUR, false, false));
        list.add(c(2, "b", Category.GAMES, 1, 2 * HOUR, false, false));
        Map<Category, Double> share = new EnumMap<>(Category.class);
        share.put(Category.HEALTH, 0.6);
        List<Ranked> out = ranker.rank(list, new Profile(null, share, true), NOW, 2);
        assertEquals(1, out.get(0).candidate.id);
        assertTrue(has(out.get(0), ReasonType.READ_OFTEN));
    }
}
