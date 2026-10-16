package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.newshub.app.data.db.entity.FavoriteEntity;
import com.newshub.app.data.db.model.FeedRow;

import java.util.List;

@Dao
public interface FavoriteDao {

    String ROW_COLUMNS = "a.clusterId AS clusterId, COALESCE(c.sourceCount, 1) AS sourceCount, "
            + "COALESCE(c.articleCount, 1) AS articleCount, "
            + "COALESCE(c.firstPublishedAt, a.publishedAt) AS firstPublishedAt, "
            + "COALESCE(c.lastPublishedAt, a.publishedAt) AS lastPublishedAt, "
            + "a.categoryMask AS categoryMask, a.primaryCategory AS primaryCategory, a.political AS political, "
            + "a.politicsScore AS politicsScore, a.id AS articleId, a.title AS title, a.summary AS summary, "
            + "a.imageUrl AS imageUrl, a.url AS url, a.publishedAt AS publishedAt, a.sourceId AS sourceId, "
            + "a.sourceName AS sourceName, a.politicsMatches AS politicsMatches, a.isDemo AS isDemo";

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void add(FavoriteEntity favorite);

    @Query("DELETE FROM favorites WHERE articleId = :articleId")
    void remove(long articleId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE articleId = :articleId)")
    boolean isFavorite(long articleId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE articleId = :articleId)")
    LiveData<Boolean> observeIsFavorite(long articleId);

    @Query("SELECT " + ROW_COLUMNS + ", 1 AS isFavorite, "
            + "EXISTS(SELECT 1 FROM history h WHERE h.articleId = a.id) AS isRead "
            + "FROM favorites f JOIN articles a ON a.id = f.articleId "
            + "LEFT JOIN clusters c ON c.id = a.clusterId ORDER BY f.addedAt DESC")
    LiveData<List<FeedRow>> observeFavorites();

    @Query("SELECT " + ROW_COLUMNS + ", 1 AS isFavorite, "
            + "EXISTS(SELECT 1 FROM history h WHERE h.articleId = a.id) AS isRead "
            + "FROM favorites f JOIN articles a ON a.id = f.articleId "
            + "LEFT JOIN clusters c ON c.id = a.clusterId ORDER BY f.addedAt DESC")
    List<FeedRow> getFavorites();

    @Query("SELECT COUNT(*) FROM favorites")
    int count();
}
