package com.newshub.app.ui.common;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;
import com.newshub.app.R;

/** Binds the shared empty / error / no-results layout (view_state.xml). */
public final class StateView {

    private final View root;
    private final ImageView icon;
    private final TextView title;
    private final TextView message;
    private final MaterialButton action;

    public StateView(View root) {
        this.root = root;
        icon = root.findViewById(R.id.state_icon);
        title = root.findViewById(R.id.state_title);
        message = root.findViewById(R.id.state_message);
        action = root.findViewById(R.id.state_action);
    }

    public void show(@DrawableRes int iconRes, @StringRes int titleRes, CharSequence messageText,
                     @StringRes int actionRes, View.OnClickListener onAction) {
        icon.setImageResource(iconRes);
        title.setText(titleRes);
        message.setText(messageText);
        message.setVisibility(messageText == null || messageText.length() == 0 ? View.GONE : View.VISIBLE);
        if (actionRes != 0 && onAction != null) {
            action.setText(actionRes);
            action.setOnClickListener(onAction);
            action.setVisibility(View.VISIBLE);
        } else {
            action.setVisibility(View.GONE);
        }
        root.setVisibility(View.VISIBLE);
    }

    public void hide() {
        root.setVisibility(View.GONE);
    }
}
