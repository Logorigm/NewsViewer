package com.newshub.app.data.db.model;

/**
 * One row of a feed: a story (cluster) with its representative publication.
 * Column names of the feed queries map 1:1 to these fields.
 */
public class FeedRow {
    public long clusterId;
    public int sourceCount;
    public int articleCount;
    public long firstPublishedAt;
    public long lastPublishedAt;
    public int categoryMask;
    public String primaryCategory;
    public boolean political;
    public int politicsScore;

    public long articleId;
    public String title;
    public String summary;
    public String imageUrl;
    public String url;
    public long publishedAt;
    public String sourceId;
    public String sourceName;
    public String politicsMatches;
    public boolean isDemo;
    public boolean isFavorite;
    public boolean isRead;
}
