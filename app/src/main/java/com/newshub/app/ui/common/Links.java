package com.newshub.app.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.newshub.app.R;

/** Opens originals in Custom Tabs (falls back to any browser) and shares links. */
public final class Links {

    private Links() {
    }

    public static void open(Context context, String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(context, R.string.error_no_link, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse(url.trim());
        try {
            CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(ContextCompat.getColor(context, R.color.nh_background))
                    .build();
            CustomTabsIntent intent = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setDefaultColorSchemeParams(colors)
                    .build();
            intent.launchUrl(context, uri);
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void share(Context context, String title, String url) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .putExtra(Intent.EXTRA_TEXT, title + "\n" + url);
        try {
            context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.error_no_share_target, Toast.LENGTH_SHORT).show();
        }
    }
}
