package com.newshub.app.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {
    @PrimaryKey
    public long articleId;
    public long addedAt;

    public FavoriteEntity() {
    }

    public FavoriteEntity(long articleId, long addedAt) {
        this.articleId = articleId;
        this.addedAt = addedAt;
    }
}
