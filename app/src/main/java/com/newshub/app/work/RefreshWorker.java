package com.newshub.app.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.newshub.app.AppContainer;
import com.newshub.app.NewsHubApp;
import com.newshub.app.data.repo.RefreshSummary;

/** Periodic background refresh. Runs on WorkManager's background thread. */
public class RefreshWorker extends Worker {

    private static final int MAX_RETRIES = 3;

    public RefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppContainer c = NewsHubApp.container(getApplicationContext());
        RefreshSummary summary = c.news.refreshBlocking(false);
        c.news.cleanupIfDue(System.currentTimeMillis());
        if (!summary.skipped) {
            NewsNotifier.notifyIfNeeded(getApplicationContext(), c, summary);
        }
        if (summary.looksOffline() && getRunAttemptCount() < MAX_RETRIES) {
            return Result.retry();
        }
        return Result.success();
    }
}
