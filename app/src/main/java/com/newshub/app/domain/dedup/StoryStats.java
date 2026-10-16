package com.newshub.app.domain.dedup;

import com.newshub.app.domain.Category;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregates the publications of one story cluster into the values shown on the story card. */
public final class StoryStats {

    /** Minimal view of a publication needed for aggregation. */
    public static final class Member {
        public final String sourceId;
        public final long publishedAt;
        public final int categoryMask;
        public final String primaryCategory;
        public final int politicsScore;
        public final boolean political;

        public Member(String sourceId, long publishedAt, int categoryMask, String primaryCategory,
                      int politicsScore, boolean political) {
            this.sourceId = sourceId;
            this.publishedAt = publishedAt;
            this.categoryMask = categoryMask;
            this.primaryCategory = primaryCategory;
            this.politicsScore = politicsScore;
            this.political = political;
        }
    }

    public int articleCount;
    public int sourceCount;
    public long firstPublishedAt;
    public long lastPublishedAt;
    public int categoryMask;
    public String primaryCategory = Category.WORLD.key;
    public boolean political;
    public int politicsScore;

    public static StoryStats aggregate(List<Member> members) {
        StoryStats s = new StoryStats();
        if (members.isEmpty()) return s;
        Set<String> sources = new HashSet<>();
        Map<Category, Integer> primaryVotes = new EnumMap<>(Category.class);
        Category firstPrimary = null;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        int politicalVotes = 0;
        for (Member m : members) {
            sources.add(m.sourceId);
            if (m.publishedAt < first) {
                first = m.publishedAt;
                firstPrimary = Category.fromKey(m.primaryCategory, Category.WORLD);
            }
            last = Math.max(last, m.publishedAt);
            s.categoryMask |= m.categoryMask;
            s.politicsScore = Math.max(s.politicsScore, m.politicsScore);
            if (m.political) politicalVotes++;
            Category c = Category.fromKey(m.primaryCategory, Category.WORLD);
            primaryVotes.merge(c, 1, Integer::sum);
        }
        s.articleCount = members.size();
        s.sourceCount = sources.size();
        s.firstPublishedAt = first;
        s.lastPublishedAt = last;
        // A story is political when at least half of its reports are political.
        s.political = politicalVotes * 2 >= members.size() && politicalVotes > 0;
        Category best = firstPrimary;
        int bestVotes = best == null ? 0 : primaryVotes.getOrDefault(best, 0);
        for (Map.Entry<Category, Integer> e : primaryVotes.entrySet()) {
            if (e.getValue() > bestVotes) {
                best = e.getKey();
                bestVotes = e.getValue();
            }
        }
        s.primaryCategory = (best == null ? Category.WORLD : best).key;
        if (s.sourceCount >= 2) s.categoryMask |= Category.MAIN.mask();
        else s.categoryMask &= ~Category.MAIN.mask();
        return s;
    }
}
