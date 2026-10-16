package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.model.FingerprintRow;
import com.newshub.app.data.db.model.MemberRow;

import java.util.List;

@Dao
public interface ArticleDao {

    /** @return row id, or -1 when an item with the same (sourceId, guidHash) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(ArticleEntity article);

    @Update
    void update(ArticleEntity article);

    @Update
    void updateAll(List<ArticleEntity> articles);

    @Query("SELECT guidHash FROM articles WHERE sourceId = :sourceId AND guidHash IN (:hashes)")
    List<String> existingHashes(String sourceId, List<String> hashes);

    @Query("SELECT * FROM articles WHERE id = :id")
    ArticleEntity get(long id);

    @Query("SELECT * FROM articles WHERE id = :id")
    LiveData<ArticleEntity> observe(long id);

    @Query("SELECT * FROM articles WHERE clusterId = :clusterId ORDER BY publishedAt ASC")
    List<ArticleEntity> byCluster(long clusterId);

    @Query("SELECT * FROM articles WHERE clusterId = :clusterId ORDER BY publishedAt ASC")
    LiveData<List<ArticleEntity>> observeByCluster(long clusterId);

    @Query("SELECT sourceId, publishedAt, categoryMask, primaryCategory, politicsScore, political "
            + "FROM articles WHERE clusterId = :clusterId")
    List<MemberRow> members(long clusterId);

    @Query("SELECT id, sourceId, url, title, summary, publishedAt, clusterId FROM articles "
            + "WHERE publishedAt >= :since ORDER BY publishedAt DESC LIMIT :limit")
    List<FingerprintRow> fingerprintsSince(long since, int limit);

    @Query("SELECT * FROM articles WHERE id > :afterId ORDER BY id LIMIT :limit")
    List<ArticleEntity> page(long afterId, int limit);

    @Query("UPDATE articles SET politicsOverride = :value WHERE clusterId = :clusterId")
    void setPoliticsOverride(long clusterId, int value);

    @Query("DELETE FROM articles WHERE publishedAt < :before AND id NOT IN (SELECT articleId FROM favorites)")
    int deleteOlderThan(long before);

    @Query("DELETE FROM articles WHERE sourceId = :sourceId AND id NOT IN (SELECT articleId FROM favorites)")
    int deleteBySource(String sourceId);

    @Query("DELETE FROM articles WHERE id NOT IN (SELECT articleId FROM favorites)")
    int deleteAllExceptFavorites();

    @Query("DELETE FROM articles WHERE isDemo = 1")
    int deleteDemo();

    @Query("SELECT COUNT(*) FROM articles")
    int count();

    @Query("SELECT DISTINCT clusterId FROM articles")
    List<Long> clusterIds();

    @Query("SELECT DISTINCT clusterId FROM articles WHERE sourceId = :sourceId")
    List<Long> clusterIdsOfSource(String sourceId);

    @Query("UPDATE articles SET sourceName = :name WHERE sourceId = :sourceId")
    void renameSource(String sourceId, String name);
}
