package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.SourceCount;

import java.util.List;

@Dao
public interface SourceDao {

    @Query("SELECT * FROM sources WHERE deleted = 0 ORDER BY sortOrder, name")
    LiveData<List<SourceEntity>> observeAll();

    @Query("SELECT * FROM sources WHERE deleted = 0 ORDER BY sortOrder, name")
    List<SourceEntity> getAll();

    @Query("SELECT * FROM sources")
    List<SourceEntity> getAllIncludingDeleted();

    @Query("SELECT * FROM sources WHERE enabled = 1 AND deleted = 0 ORDER BY sortOrder, name")
    List<SourceEntity> getEnabled();

    @Query("SELECT * FROM sources WHERE id = :id")
    SourceEntity get(String id);

    @Query("SELECT * FROM sources WHERE id = :id")
    LiveData<SourceEntity> observe(String id);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<SourceEntity> sources);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SourceEntity source);

    @Update
    void update(SourceEntity source);

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    void setEnabled(String id, boolean enabled);

    @Query("DELETE FROM sources WHERE id = :id")
    void delete(String id);

    @Query("SELECT sourceId, COUNT(*) AS count FROM articles GROUP BY sourceId")
    LiveData<List<SourceCount>> observeArticleCounts();

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM sources")
    int maxSortOrder();
}
