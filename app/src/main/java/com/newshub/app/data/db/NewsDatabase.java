package com.newshub.app.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.newshub.app.data.db.dao.ArticleDao;
import com.newshub.app.data.db.dao.ClusterDao;
import com.newshub.app.data.db.dao.FavoriteDao;
import com.newshub.app.data.db.dao.FeedDao;
import com.newshub.app.data.db.dao.HistoryDao;
import com.newshub.app.data.db.dao.SettingsDao;
import com.newshub.app.data.db.dao.SourceDao;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;
import com.newshub.app.data.db.entity.FavoriteEntity;
import com.newshub.app.data.db.entity.HistoryEntity;
import com.newshub.app.data.db.entity.SettingEntity;
import com.newshub.app.data.db.entity.SourceEntity;

@Database(entities = {SourceEntity.class, ArticleEntity.class, ClusterEntity.class, FavoriteEntity.class,
        HistoryEntity.class, SettingEntity.class}, version = 1, exportSchema = true)
public abstract class NewsDatabase extends RoomDatabase {

    public static final String NAME = "newshub.db";

    public abstract SourceDao sources();

    public abstract ArticleDao articles();

    public abstract ClusterDao clusters();

    public abstract FavoriteDao favorites();

    public abstract HistoryDao history();

    public abstract SettingsDao settings();

    public abstract FeedDao feed();

    public static NewsDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), NewsDatabase.class, NAME).build();
    }

    public static SupportSQLiteQuery toQuery(FeedQueryBuilder.Built built) {
        return new SimpleSQLiteQuery(built.sql, built.args);
    }
}
