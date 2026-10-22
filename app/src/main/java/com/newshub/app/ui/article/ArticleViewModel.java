package com.newshub.app.ui.article;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.db.entity.ArticleEntity;
import com.newshub.app.data.db.entity.ClusterEntity;

import java.util.List;

public class ArticleViewModel extends AndroidViewModel {

    private final AppContainer c;
    private long articleId = -1;
    private long clusterId = -1;
    private LiveData<ArticleEntity> article;
    private LiveData<Boolean> favorite;
    private LiveData<ClusterEntity> cluster;
    private LiveData<List<ArticleEntity>> clusterArticles;
    private boolean markedRead;

    public ArticleViewModel(@NonNull Application app) {
        super(app);
        c = NewsHubApp.container(app);
    }

    public void init(long id) {
        if (articleId == id) return;
        articleId = id;
        article = c.news.observeArticle(id);
        favorite = c.news.observeFavorite(id);
    }

    public LiveData<ArticleEntity> article() {
        return article;
    }

    public LiveData<Boolean> favorite() {
        return favorite;
    }

    public LiveData<ClusterEntity> cluster(long id) {
        ensureCluster(id);
        return cluster;
    }

    public LiveData<List<ArticleEntity>> clusterArticles(long id) {
        ensureCluster(id);
        return clusterArticles;
    }

    private void ensureCluster(long id) {
        if (clusterId == id && cluster != null) return;
        clusterId = id;
        cluster = c.news.observeCluster(id);
        clusterArticles = c.news.observeClusterArticles(id);
    }

    public void markReadOnce() {
        if (markedRead || articleId <= 0) return;
        markedRead = true;
        c.news.markRead(articleId);
    }

    public void toggleFavorite() {
        c.news.toggleFavorite(articleId);
    }

    public void setPoliticsOverride(long cluster, int value) {
        c.news.setPoliticsOverride(cluster, value);
    }
}
