package com.newshub.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A story: one or more publications about the same event. */
@Entity(tableName = "clusters",
        indices = {@Index(value = {"firstPublishedAt"}), @Index(value = {"lastPublishedAt"})})
public class ClusterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long firstPublishedAt;
    public long lastPublishedAt;
    public int sourceCount;
    public int articleCount;
    public int categoryMask;
    @NonNull
    public String primaryCategory = "world";
    public boolean political;
    public int politicsScore;
}
