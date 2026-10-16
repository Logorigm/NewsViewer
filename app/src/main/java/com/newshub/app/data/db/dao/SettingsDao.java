package com.newshub.app.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.newshub.app.data.db.entity.SettingEntity;

import java.util.List;

@Dao
public interface SettingsDao {

    @Query("SELECT * FROM settings")
    List<SettingEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(SettingEntity setting);

    @Query("DELETE FROM settings WHERE settingKey = :key")
    void remove(String key);
}
