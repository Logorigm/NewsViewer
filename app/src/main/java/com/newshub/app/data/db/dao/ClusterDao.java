package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.newshub.app.data.db.entity.ClusterEntity;

import java.util.List;

@Dao
public interface ClusterDao {

    @Insert
    long insert(ClusterEntity cluster);

    @Update
    void update(ClusterEntity cluster);

    @Query("SELECT * FROM clusters WHERE id = :id")
    ClusterEntity get(long id);

    @Query("SELECT * FROM clusters WHERE id = :id")
    LiveData<ClusterEntity> observe(long id);

    @Query("DELETE FROM clusters WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM clusters WHERE id NOT IN (SELECT DISTINCT clusterId FROM articles)")
    int deleteOrphans();

    /** Category bitmasks of visible stories since a moment (for the category screen counters). */
    @Query("SELECT c.categoryMask FROM clusters c WHERE c.firstPublishedAt >= :since AND EXISTS ("
            + "SELECT 1 FROM articles x JOIN sources s ON s.id = x.sourceId "
            + "WHERE x.clusterId = c.id AND s.enabled = 1 AND s.deleted = 0)")
    LiveData<List<Integer>> observeMasksSince(long since);
}
