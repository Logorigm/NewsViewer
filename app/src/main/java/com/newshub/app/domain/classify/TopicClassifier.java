package com.newshub.app.domain.classify;

import com.newshub.app.domain.Category;
import com.newshub.app.domain.text.TextNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns categories using keyword rules plus the default category of the source, and evaluates
 * the politics rules. Fully offline and deterministic; no paid or remote AI services are used.
 */
public final class TopicClassifier {

    /** Minimum score for a category to be attached to a publication. */
    static final double ASSIGN_THRESHOLD = 2.0;
    /** Bonus for the category the user assigned to the source. */
    static final double SOURCE_PRIOR = 1.5;
    private static final int MAX_CATEGORIES = 3;

    private final Map<Category, List<Term>> topicTerms;
    private final PoliticsRules politics;

    public TopicClassifier(PoliticsRules politics) {
        this(TopicRules.defaults(), politics);
    }

    public TopicClassifier(Map<Category, List<Term>> topicTerms, PoliticsRules politics) {
        this.topicTerms = topicTerms;
        this.politics = politics;
    }

    public PoliticsRules politicsRules() {
        return politics;
    }

    public Classification classify(String title, String summary, List<String> tags, Category sourceCategory) {
        List<String> titleTokens = TextNormalizer.tokens(title);
        List<String> bodyTokens = TextNormalizer.tokens(summary);
        List<String> tagTokens = TextNormalizer.tokens(tags == null ? "" : String.join(" ", tags));
        Category prior = sourceCategory == null || sourceCategory == Category.MAIN ? Category.WORLD : sourceCategory;

        Map<Category, Double> scores = new EnumMap<>(Category.class);
        for (Category c : Category.assignable()) {
            double s = 0;
            List<Term> terms = topicTerms.get(c);
            if (terms != null) {
                for (Term t : terms) {
                    if (t.matches(titleTokens)) {
                        s += 2.0 * t.weight;
                    } else if (t.matches(bodyTokens)) {
                        s += t.weight;
                    }
                    if (!tagTokens.isEmpty() && t.matches(tagTokens)) s += 2.0;
                }
            }
            if (c == prior) s += SOURCE_PRIOR;
            scores.put(c, s);
        }

        List<Map.Entry<Category, Double>> ranked = new ArrayList<>(scores.entrySet());
        Collections.sort(ranked, (a, b) -> {
            int cmp = Double.compare(b.getValue(), a.getValue());
            if (cmp != 0) return cmp;
            if (a.getKey() == prior) return -1;
            if (b.getKey() == prior) return 1;
            return a.getKey().compareTo(b.getKey());
        });

        Category primary = ranked.get(0).getValue() >= ASSIGN_THRESHOLD ? ranked.get(0).getKey() : prior;
        List<Category> categories = new ArrayList<>();
        categories.add(primary);
        for (Map.Entry<Category, Double> e : ranked) {
            if (categories.size() >= MAX_CATEGORIES) break;
            if (e.getValue() >= ASSIGN_THRESHOLD && !categories.contains(e.getKey())) categories.add(e.getKey());
        }

        PoliticsResult p = politics.evaluate(title, summary);
        return new Classification(primary, categories, p);
    }
}
