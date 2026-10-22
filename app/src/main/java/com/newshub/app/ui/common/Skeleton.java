package com.newshub.app.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.newshub.app.R;

/** Placeholder cards with a gentle pulse while the first data loads. */
public final class Skeleton {

    private Skeleton() {
    }

    private static final int COUNT = 6;

    public static void show(ViewGroup container) {
        if (container.getChildCount() == 0) {
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            for (int i = 0; i < COUNT; i++) {
                container.addView(inflater.inflate(R.layout.item_skeleton, container, false));
            }
        }
        if (container.getVisibility() == View.VISIBLE) return;
        container.setVisibility(View.VISIBLE);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(container, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(850);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
        container.setTag(pulse);
    }

    public static void hide(ViewGroup container) {
        Object tag = container.getTag();
        if (tag instanceof ObjectAnimator) ((ObjectAnimator) tag).cancel();
        container.setTag(null);
        container.setAlpha(1f);
        container.setVisibility(View.GONE);
    }
}
