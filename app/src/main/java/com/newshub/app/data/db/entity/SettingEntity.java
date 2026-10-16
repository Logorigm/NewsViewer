package com.newshub.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** Key/value user setting. */
@Entity(tableName = "settings")
public class SettingEntity {
    @PrimaryKey
    @NonNull
    public String settingKey = "";
    @NonNull
    public String settingValue = "";

    public SettingEntity() {
    }

    @Ignore
    public SettingEntity(@NonNull String key, @NonNull String value) {
        this.settingKey = key;
        this.settingValue = value;
    }
}
