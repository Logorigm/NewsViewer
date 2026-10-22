package com.newshub.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.RawQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;
import com.newshub.app.data.db.entity.FavoriteEntity;
import com.newshub.app.data.db.entity.HistoryEntity;
import com.newshub.app.data.db.entity.SourceEntity;
import com.newshub.app.data.db.model.FeedRow;

import java.util.List;

/** Dynamic feed queries built by {@link com.newshub.app.data.db.FeedQueryBuilder}. */
@Dao
public interface FeedDao {

    @RawQuery(observedEntities = {ClusterEntity.class, ArticleEntity.class, SourceEntity.class,
            FavoriteEntity.class, HistoryEntity.class})
    LiveData<List<FeedRow>> observe(SupportSQLiteQuery query);

    @RawQuery
    List<FeedRow> query(SupportSQLiteQuery query);

    @RawQuery(observedEntities = {ClusterEntity.class, ArticleEntity.class, SourceEntity.class})
    LiveData<Integer> observeCount(SupportSQLiteQuery query);

    @RawQuery
    int count(SupportSQLiteQuery query);
}
