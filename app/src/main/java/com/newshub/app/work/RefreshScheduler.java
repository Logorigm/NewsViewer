package com.newshub.app.work;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.newshub.app.data.repo.SettingsRepository;

import java.util.concurrent.TimeUnit;

/** (Re)schedules the periodic refresh according to the user's settings. */
public final class RefreshScheduler {

    private RefreshScheduler() {
    }

    public static final String UNIQUE_NAME = "newshub-periodic-refresh";

    public static void schedule(Context context, SettingsRepository settings) {
        WorkManager wm = WorkManager.getInstance(context);
        int minutes = settings.refreshIntervalMinutes();
        if (minutes <= 0) {
            wm.cancelUniqueWork(UNIQUE_NAME);
            return;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(settings.wifiOnly() ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        // WorkManager's minimum period is 15 minutes.
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(RefreshWorker.class,
                Math.max(15, minutes), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
