package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.newshub.app.data.db.entity.HistoryEntity;
import com.newshub.app.data.db.model.CategoryCount;
import com.newshub.app.data.db.model.FeedRow;

import java.util.List;

@Dao
public interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void add(HistoryEntity entry);

    @Query("DELETE FROM history")
    void clear();

    @Query("DELETE FROM history WHERE articleId NOT IN (SELECT id FROM articles)")
    int deleteOrphans();

    @Query("SELECT " + FavoriteDao.ROW_COLUMNS + ", "
            + "EXISTS(SELECT 1 FROM favorites f WHERE f.articleId = a.id) AS isFavorite, 1 AS isRead "
            + "FROM history h JOIN articles a ON a.id = h.articleId "
            + "LEFT JOIN clusters c ON c.id = a.clusterId ORDER BY h.readAt DESC LIMIT 500")
    LiveData<List<FeedRow>> observeHistory();

    @Query("SELECT a.primaryCategory AS category, COUNT(*) AS count FROM history h "
            + "JOIN articles a ON a.id = h.articleId WHERE h.readAt >= :since GROUP BY a.primaryCategory")
    List<CategoryCount> readCountsSince(long since);
}
