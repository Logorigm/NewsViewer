package com.newshub.app.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.newshub.app.R;
import com.newshub.app.domain.Category;

/** Display names and marker colors of categories. */
public final class CategoryUi {

    private CategoryUi() {
    }

    @StringRes
    public static int title(Category c) {
        switch (c) {
            case MAIN:
                return R.string.cat_main;
            case TECH:
                return R.string.cat_tech;
            case AI:
                return R.string.cat_ai;
            case SCIENCE:
                return R.string.cat_science;
            case GAMES:
                return R.string.cat_games;
            case INTERNET:
                return R.string.cat_internet;
            case ENTERTAINMENT:
                return R.string.cat_entertainment;
            case INCIDENTS:
                return R.string.cat_incidents;
            case ECONOMY:
                return R.string.cat_economy;
            case SPORT:
                return R.string.cat_sport;
            case HEALTH:
                return R.string.cat_health;
            case WORLD:
            default:
                return R.string.cat_world;
        }
    }

    @ColorRes
    public static int color(Category c) {
        switch (c) {
            case MAIN:
                return R.color.cat_main;
            case TECH:
                return R.color.cat_tech;
            case AI:
                return R.color.cat_ai;
            case SCIENCE:
                return R.color.cat_science;
            case GAMES:
                return R.color.cat_games;
            case INTERNET:
                return R.color.cat_internet;
            case ENTERTAINMENT:
                return R.color.cat_entertainment;
            case INCIDENTS:
                return R.color.cat_incidents;
            case ECONOMY:
                return R.color.cat_economy;
            case SPORT:
                return R.color.cat_sport;
            case HEALTH:
                return R.color.cat_health;
            case WORLD:
            default:
                return R.color.cat_world;
        }
    }

    public static String title(Context context, String key) {
        return context.getString(title(Category.fromKey(key, Category.WORLD)));
    }

    /** Small colored dot used before the category name on cards. */
    public static GradientDrawable dot(Context context, Category c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(ContextCompat.getColor(context, color(c)));
        int size = Dimens.dp(context, 7);
        d.setSize(size, size);
        d.setBounds(0, 0, size, size);
        return d;
    }
}
