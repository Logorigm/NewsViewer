package com.newshub.app.ui.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.newshub.app.R;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.domain.Category;
import com.newshub.app.ui.common.CategoryUi;

/** "Больше / Обычно / Меньше" per topic for the "Только интересное" ranking. */
public final class InterestsDialog {

    private InterestsDialog() {
    }

    public static void show(Context context, SettingsRepository settings, Runnable onChanged) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_interests, null, false);
        ViewGroup rows = content.findViewById(R.id.rows);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Category c : Category.assignable()) {
            View row = inflater.inflate(R.layout.item_interest_row, rows, false);
            TextView name = row.findViewById(R.id.name);
            name.setText(CategoryUi.title(c));
            MaterialButtonToggleGroup group = row.findViewById(R.id.group);
            int value = settings.interest(c);
            group.check(value > 0 ? R.id.more : value < 0 ? R.id.less : R.id.neutral);
            group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
                if (!isChecked) return;
                int v = checkedId == R.id.more ? 1 : checkedId == R.id.less ? -1 : 0;
                settings.setInterest(c, v);
                if (onChanged != null) onChanged.run();
            });
            rows.addView(row);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.interests_title)
                .setView(content)
                .setPositiveButton(R.string.action_done, null)
                .setNeutralButton(R.string.interests_reset, (d, w) -> {
                    settings.resetInterests();
                    if (onChanged != null) onChanged.run();
                })
                .show();
    }
}
