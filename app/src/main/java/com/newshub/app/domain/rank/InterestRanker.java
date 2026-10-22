package com.newshub.app.domain.rank;

import com.newshub.app.domain.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking for the "Только интересное" mode.
 *
 * <p>score = 0.45·freshness + 0.20·independent reports + 0.35·topic interest, then a greedy
 * selection that penalizes repeating the same source or topic and caps every source at 20% of
 * the selection, so one prolific source cannot fill the feed. The number of sources is used as
 * "how widely reported", never as a measure of truth. Every selected item carries the reasons
 * that put it there.
 */
public final class InterestRanker {

    public static final double MAX_SOURCE_SHARE = 0.2;
    private static final double FRESH_HALF_LIFE_HOURS = 18.0;

    private static final Map<Category, Double> BASE_INTEREST = new EnumMap<>(Category.class);

    static {
        BASE_INTEREST.put(Category.MAIN, 0.30);
        BASE_INTEREST.put(Category.TECH, 0.65);
        BASE_INTEREST.put(Category.AI, 0.70);
        BASE_INTEREST.put(Category.SCIENCE, 0.70);
        BASE_INTEREST.put(Category.GAMES, 0.60);
        BASE_INTEREST.put(Category.INTERNET, 0.55);
        BASE_INTEREST.put(Category.ENTERTAINMENT, 0.55);
        BASE_INTEREST.put(Category.WORLD, 0.25);
        BASE_INTEREST.put(Category.INCIDENTS, 0.20);
        BASE_INTEREST.put(Category.ECONOMY, 0.40);
        BASE_INTEREST.put(Category.SPORT, 0.40);
        BASE_INTEREST.put(Category.HEALTH, 0.45);
    }

    public static double baseInterest(Category c) {
        Double v = BASE_INTEREST.get(c);
        return v == null ? 0.3 : v;
    }

    /** One story competing for a place in the selection. */
    public static final class Candidate {
        public final long id;
        public final String sourceId;
        public final Category primary;
        public final int sourceCount;
        public final long publishedAt;
        public final boolean political;
        public final boolean read;

        public Candidate(long id, String sourceId, Category primary, int sourceCount, long publishedAt,
                         boolean political, boolean read) {
            this.id = id;
            this.sourceId = sourceId;
            this.primary = primary;
            this.sourceCount = sourceCount;
            this.publishedAt = publishedAt;
            this.political = political;
            this.read = read;
        }
    }

    /** User preferences: explicit (-1 less, 0 neutral, +1 more) and the share of reads per topic. */
    public static final class Profile {
        public final Map<Category, Integer> explicit;
        public final Map<Category, Double> readShare;
        public final boolean useHistory;

        public Profile(Map<Category, Integer> explicit, Map<Category, Double> readShare, boolean useHistory) {
            this.explicit = explicit == null ? Collections.emptyMap() : explicit;
            this.readShare = readShare == null ? Collections.emptyMap() : readShare;
            this.useHistory = useHistory;
        }

        public static Profile neutral() {
            return new Profile(null, null, false);
        }

        int explicitFor(Category c) {
            Integer v = explicit.get(c);
            return v == null ? 0 : v;
        }
    }

    public enum ReasonType { FRESH, MULTI_SOURCE, LIKED_TOPIC, READ_OFTEN, DEFAULT_TOPIC }

    /** Why an item was selected. {@code value} is hours for FRESH and sources for MULTI_SOURCE. */
    public static final class Reason {
        public final ReasonType type;
        public final Category category;
        public final int value;

        Reason(ReasonType type, Category category, int value) {
            this.type = type;
            this.category = category;
            this.value = value;
        }
    }

    public static final class Ranked {
        public final Candidate candidate;
        public final double score;
        public final List<Reason> reasons;

        Ranked(Candidate candidate, double score, List<Reason> reasons) {
            this.candidate = candidate;
            this.score = score;
            this.reasons = Collections.unmodifiableList(reasons);
        }
    }

    public List<Ranked> rank(List<Candidate> candidates, Profile profile, long now, int limit) {
        List<Ranked> scored = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) scored.add(score(c, profile, now));

        int maxPerSource = Math.max(2, (int) Math.ceil(limit * MAX_SOURCE_SHARE));
        Map<String, Integer> perSource = new HashMap<>();
        List<Ranked> selected = new ArrayList<>();
        boolean[] used = new boolean[scored.size()];

        while (selected.size() < limit) {
            int bestIdx = -1;
            double bestValue = -Double.MAX_VALUE;
            for (int i = 0; i < scored.size(); i++) {
                if (used[i]) continue;
                Ranked r = scored.get(i);
                int fromSource = perSource.getOrDefault(r.candidate.sourceId, 0);
                if (fromSource >= maxPerSource) continue;
                double value = r.score - 0.12 * fromSource - 0.06 * recentSameTopic(selected, r.candidate.primary);
                if (value > bestValue) {
                    bestValue = value;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) break;
            used[bestIdx] = true;
            Ranked pick = scored.get(bestIdx);
            selected.add(pick);
            perSource.merge(pick.candidate.sourceId, 1, Integer::sum);
        }
        return selected;
    }

    private static int recentSameTopic(List<Ranked> selected, Category c) {
        int n = 0;
        for (int i = Math.max(0, selected.size() - 4); i < selected.size(); i++) {
            if (selected.get(i).candidate.primary == c) n++;
        }
        return n;
    }

    Ranked score(Candidate c, Profile p, long now) {
        List<Reason> reasons = new ArrayList<>();
        double ageHours = Math.max(0, (now - c.publishedAt) / 3_600_000.0);
        double fresh = Math.pow(0.5, ageHours / FRESH_HALF_LIFE_HOURS);
        if (ageHours < 6) reasons.add(new Reason(ReasonType.FRESH, c.primary, (int) Math.floor(ageHours)));

        double confirm = 0;
        if (c.sourceCount >= 2) {
            confirm = Math.min(1.0, Math.log(c.sourceCount) / Math.log(5));
            reasons.add(new Reason(ReasonType.MULTI_SOURCE, c.primary, c.sourceCount));
        }

        double base = baseInterest(c.primary);
        int explicit = p.explicitFor(c.primary);
        double topic = explicit > 0 ? Math.min(1.0, base + 0.35) : explicit < 0 ? Math.max(0, base - 0.35) : base;
        if (explicit > 0) {
            reasons.add(new Reason(ReasonType.LIKED_TOPIC, c.primary, 0));
        }
        if (p.useHistory) {
            Double share = p.readShare.get(c.primary);
            double boost = share == null ? 0 : 0.3 * Math.min(1.0, share * 3);
            if (boost >= 0.1) reasons.add(new Reason(ReasonType.READ_OFTEN, c.primary, 0));
            topic = Math.min(1.0, topic + boost);
        }
        if (explicit == 0 && base >= 0.55) reasons.add(new Reason(ReasonType.DEFAULT_TOPIC, c.primary, 0));

        double score = 0.45 * fresh + 0.20 * confirm + 0.35 * topic;
        if (explicit < 0) score *= 0.6;
        if (c.read) score *= 0.5;
        if (c.political) score *= 0.6;
        return new Ranked(c, score, reasons);
    }
}
