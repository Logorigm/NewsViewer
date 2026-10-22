package com.newshub.app.work;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.newshub.app.AppContainer;
import com.newshub.app.R;
import com.newshub.app.data.db.model.FeedRow;
import com.newshub.app.data.repo.RefreshSummary;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.domain.feed.FeedFilter;
import com.newshub.app.domain.feed.FeedMode;
import com.newshub.app.domain.feed.Period;
import com.newshub.app.ui.MainActivity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Optional notifications about new stories after a background refresh. */
public final class NewsNotifier {

    private NewsNotifier() {
    }

    public static final String CHANNEL_ID = "new_stories";
    private static final int NOTIFICATION_ID = 1001;

    public static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notification_channel_description));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    @SuppressLint("MissingPermission")
    public static void notifyIfNeeded(Context context, AppContainer c, RefreshSummary summary) {
        SettingsRepository settings = c.settings;
        if (!settings.notificationsEnabled() || !canPost(context)) return;

        FeedFilter filter = settings.baseFilter(FeedMode.ALL, null).withPeriod(Period.DAY);
        List<FeedRow> rows = c.news.queryFeed(filter, 50);
        String headline = null;
        int count;
        if (settings.notifyImportantOnly()) {
            Set<Long> important = new HashSet<>(summary.importantClusterIds);
            count = 0;
            for (FeedRow r : rows) {
                if (!important.contains(r.clusterId)) continue;
                if (r.political && settings.politicsFilterEnabled()) continue;
                if (headline == null) headline = r.title;
                count++;
            }
        } else {
            count = summary.newStories;
            for (FeedRow r : rows) {
                if (r.political && settings.politicsFilterEnabled()) continue;
                headline = r.title;
                break;
            }
        }
        if (count <= 0 || headline == null) return;

        Intent open = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = settings.notifyImportantOnly()
                ? context.getResources().getQuantityString(R.plurals.notification_important, count, count)
                : context.getResources().getQuantityString(R.plurals.notification_new, count, count);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_news)
                .setContentTitle(title)
                .setContentText(headline)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(headline))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build());
        } catch (SecurityException ignored) {
            // permission revoked between the check and the call
        }
    }
}
