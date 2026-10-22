package com.newshub.app.domain.feed;

import com.newshub.app.domain.Category;

import java.util.Objects;

/** Immutable description of what a feed screen shows. */
public final class FeedFilter {

    public final FeedMode mode;
    public final Category category;
    public final Period period;
    public final SortOrder sort;
    public final String sourceId;
    public final String query;
    public final int limit;
    public final boolean groupDuplicates;
    public final boolean politicsFilterEnabled;
    public final PoliticsMode politicsMode;

    public FeedFilter(FeedMode mode, Category category, Period period, SortOrder sort, String sourceId,
                      String query, int limit, boolean groupDuplicates, boolean politicsFilterEnabled,
                      PoliticsMode politicsMode) {
        this.mode = mode;
        this.category = category;
        this.period = period;
        this.sort = sort;
        this.sourceId = sourceId == null || sourceId.isEmpty() ? null : sourceId;
        this.query = query == null || query.trim().isEmpty() ? null : query.trim();
        this.limit = limit;
        this.groupDuplicates = groupDuplicates;
        this.politicsFilterEnabled = politicsFilterEnabled;
        this.politicsMode = politicsMode;
    }

    public static FeedFilter defaults() {
        return new FeedFilter(FeedMode.ALL, null, Period.WEEK, SortOrder.NEWEST, null, null, 100, true,
                true, PoliticsMode.LIMIT);
    }

    /** Political stories are excluded entirely. */
    public boolean hidesPolitics() {
        if (mode == FeedMode.POLITICAL_ONLY) return false;
        if (mode == FeedMode.NO_POLITICS) return true;
        return politicsFilterEnabled && politicsMode == PoliticsMode.HIDE;
    }

    /** Political stories are limited (only widely reported ones, capped share). */
    public boolean limitsPolitics() {
        if (mode == FeedMode.POLITICAL_ONLY || hidesPolitics()) return false;
        return politicsFilterEnabled && politicsMode == PoliticsMode.LIMIT;
    }

    public FeedFilter withMode(FeedMode m, Category c) {
        return new FeedFilter(m, c, period, sort, sourceId, query, limit, groupDuplicates, politicsFilterEnabled,
                politicsMode);
    }

    public FeedFilter withPeriod(Period p) {
        return new FeedFilter(mode, category, p, sort, sourceId, query, limit, groupDuplicates,
                politicsFilterEnabled, politicsMode);
    }

    public FeedFilter withSort(SortOrder s) {
        return new FeedFilter(mode, category, period, s, sourceId, query, limit, groupDuplicates,
                politicsFilterEnabled, politicsMode);
    }

    public FeedFilter withSource(String id) {
        return new FeedFilter(mode, category, period, sort, id, query, limit, groupDuplicates,
                politicsFilterEnabled, politicsMode);
    }

    public FeedFilter withQuery(String q) {
        return new FeedFilter(mode, category, period, sort, sourceId, q, limit, groupDuplicates,
                politicsFilterEnabled, politicsMode);
    }

    public FeedFilter withSettings(int newLimit, boolean group, boolean politicsEnabled, PoliticsMode pm) {
        return new FeedFilter(mode, category, period, sort, sourceId, query, newLimit, group, politicsEnabled, pm);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeedFilter)) return false;
        FeedFilter f = (FeedFilter) o;
        return limit == f.limit && groupDuplicates == f.groupDuplicates
                && politicsFilterEnabled == f.politicsFilterEnabled && mode == f.mode && category == f.category
                && period == f.period && sort == f.sort && Objects.equals(sourceId, f.sourceId)
                && Objects.equals(query, f.query) && politicsMode == f.politicsMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, category, period, sort, sourceId, query, limit, groupDuplicates,
                politicsFilterEnabled, politicsMode);
    }
}
