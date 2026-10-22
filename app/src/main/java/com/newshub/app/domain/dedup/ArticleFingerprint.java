package com.newshub.app.domain.dedup;

import com.newshub.app.domain.text.TextNormalizer;
import com.newshub.app.domain.text.UrlNormalizer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pre-computed features of a publication used to decide whether two items describe one event. */
public final class ArticleFingerprint {

    private static final int SHINGLE_WORDS = 30;

    public final long id;
    public final String sourceId;
    public final String canonicalUrl;
    public final long publishedAt;
    public final Set<String> titleStems;
    public final Set<String> textShingles;
    public final Set<String> numbers;
    public final Set<String> entities;

    public ArticleFingerprint(long id, String sourceId, String canonicalUrl, long publishedAt,
                              Set<String> titleStems, Set<String> textShingles, Set<String> numbers,
                              Set<String> entities) {
        this.id = id;
        this.sourceId = sourceId;
        this.canonicalUrl = canonicalUrl == null ? "" : canonicalUrl;
        this.publishedAt = publishedAt;
        this.titleStems = Collections.unmodifiableSet(titleStems);
        this.textShingles = Collections.unmodifiableSet(textShingles);
        this.numbers = Collections.unmodifiableSet(numbers);
        this.entities = Collections.unmodifiableSet(entities);
    }

    public static ArticleFingerprint of(long id, String sourceId, String url, String title, String summary,
                                        long publishedAt) {
        String cleanTitle = TextNormalizer.stripLabels(title);
        Set<String> stems = TextNormalizer.stemSet(cleanTitle);
        List<String> bodyStems = TextNormalizer.contentStems(summary);
        Set<String> shingles = new LinkedHashSet<>();
        int limit = Math.min(bodyStems.size(), SHINGLE_WORDS);
        for (int i = 0; i + 1 < limit; i++) {
            shingles.add(bodyStems.get(i) + " " + bodyStems.get(i + 1));
        }
        return new ArticleFingerprint(id, sourceId, UrlNormalizer.canonical(url), publishedAt, stems, shingles,
                TextNormalizer.numbers(cleanTitle), TextNormalizer.entities(cleanTitle));
    }
}
