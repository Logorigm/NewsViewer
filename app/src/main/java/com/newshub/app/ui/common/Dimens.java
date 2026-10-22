package com.newshub.app.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public final class Dimens {

    private Dimens() {
    }

    public static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }

    /** A tinted copy of an icon with explicit bounds, for use as a compound drawable. */
    public static Drawable smallIcon(Context context, @DrawableRes int icon, float sizeDp, @ColorRes int color) {
        Drawable d = ContextCompat.getDrawable(context, icon);
        if (d == null) return null;
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, ContextCompat.getColor(context, color));
        int size = dp(context, sizeDp);
        d.setBounds(0, 0, size, size);
        return d;
    }
}
