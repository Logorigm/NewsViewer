package com.newshub.app.ui.feed;

import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.domain.rank.InterestRanker;

import java.util.List;
import java.util.Objects;

/** A card in a list: a story (or a single publication when grouping is off) plus ranking reasons. */
public final class FeedItem {

    public final FeedRow row;
    /** Reasons from the "interesting" ranking, null in other modes. */
    public final List<InterestRanker.Reason> reasons;
    public final boolean grouped;

    public FeedItem(FeedRow row, List<InterestRanker.Reason> reasons, boolean grouped) {
        this.row = row;
        this.reasons = reasons;
        this.grouped = grouped;
    }

    public long key() {
        return grouped ? row.clusterId : row.articleId;
    }

    public long displayTime() {
        return grouped && row.firstPublishedAt > 0 ? row.firstPublishedAt : row.publishedAt;
    }

    boolean sameContent(FeedItem o) {
        FeedRow a = row;
        FeedRow b = o.row;
        return a.articleId == b.articleId && a.sourceCount == b.sourceCount && a.isFavorite == b.isFavorite
                && a.isRead == b.isRead && a.political == b.political && displayTime() == o.displayTime()
                && Objects.equals(a.title, b.title) && Objects.equals(a.summary, b.summary)
                && Objects.equals(a.imageUrl, b.imageUrl) && Objects.equals(a.primaryCategory, b.primaryCategory)
                && (reasons == null) == (o.reasons == null);
    }
}
