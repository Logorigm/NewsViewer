package com.newshub.app.ui.common;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.newshub.app.R;

/** Image loading with a neutral placeholder; broken or missing images never break a card. */
public final class Images {

    private Images() {
    }

    public static void load(ImageView view, String url) {
        Glide.with(view)
                .load(url)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(view);
    }

    public static void clear(ImageView view) {
        Glide.with(view).clear(view);
        view.setImageDrawable(null);
    }
}
