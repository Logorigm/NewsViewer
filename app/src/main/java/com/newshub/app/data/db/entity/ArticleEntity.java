package com.newshub.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One stored publication. Only a short excerpt is kept; the full text stays at the source. */
@Entity(tableName = "articles",
        indices = {
                @Index(value = {"sourceId", "guidHash"}, unique = true),
                @Index(value = {"clusterId"}),
                @Index(value = {"publishedAt"}),
                @Index(value = {"canonicalUrl"})
        })
public class ArticleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String sourceId = "";
    /** Source name at the time of saving (kept for favorites of later removed sources). */
    @NonNull
    public String sourceName = "";
    /** SHA-1 of the item's guid, used to skip already stored items. */
    @NonNull
    public String guidHash = "";
    @NonNull
    public String url = "";
    @NonNull
    public String canonicalUrl = "";
    @NonNull
    public String title = "";
    @NonNull
    public String summary = "";
    @Nullable
    public String imageUrl;
    @Nullable
    public String author;
    /** UTC epoch millis. */
    public long publishedAt;
    public long fetchedAt;
    /** The feed had no date; {@link #publishedAt} is the download time. */
    public boolean dateEstimated;
    public int categoryMask;
    @NonNull
    public String primaryCategory = "world";
    public int politicsScore;
    /** Effective politics flag (rules + user override). */
    public boolean political;
    /** 0 = automatic, 1 = user marked as political, -1 = user marked as not political. */
    public int politicsOverride;
    /** Matched politics rule words, comma separated (shown as the reason). */
    @Nullable
    public String politicsMatches;
    /** Normalized title + excerpt for search. */
    @NonNull
    public String searchText = "";
    public long clusterId;
    /** Generated demo content (debug builds only). */
    public boolean isDemo;
}
