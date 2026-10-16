package com.newshub.app.ui.common;

import androidx.appcompat.app.AppCompatDelegate;

/** Applies the theme setting. Dark is the default. */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    public static void apply(String theme) {
        int mode;
        if ("light".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("system".equals(theme)) {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }
}
