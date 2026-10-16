package com.newshub.app.data.db.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Reading history: the last time a publication was opened. */
@Entity(tableName = "history", indices = {@Index(value = {"readAt"})})
public class HistoryEntity {
    @PrimaryKey
    public long articleId;
    public long readAt;

    public HistoryEntity() {
    }

    @Ignore
    public HistoryEntity(long articleId, long readAt) {
        this.articleId = articleId;
        this.readAt = readAt;
    }
}
