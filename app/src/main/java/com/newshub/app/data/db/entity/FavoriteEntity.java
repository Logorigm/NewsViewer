package com.newshub.app.data.db.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {
    @PrimaryKey
    public long articleId;
    public long addedAt;

    public FavoriteEntity() {
    }

    @Ignore
    public FavoriteEntity(long articleId, long addedAt) {
        this.articleId = articleId;
        this.addedAt = addedAt;
    }
}
