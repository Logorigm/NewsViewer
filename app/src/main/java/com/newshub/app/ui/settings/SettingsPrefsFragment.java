package com.newshub.app.ui.settings;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.newshub.app.AppContainer;
import com.newshub.app.BuildConfig;
import com.newshub.app.DevTools;
import com.newshub.app.NewsHubApp;
import com.newshub.app.R;
import com.newshub.app.data.repo.SettingsRepository;
import com.newshub.app.ui.common.ThemeHelper;

public class SettingsPrefsFragment extends PreferenceFragmentCompat {

    private AppContainer c;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    SwitchPreferenceCompat p = findPreference(SettingsRepository.NOTIFICATIONS);
                    if (p != null) p.setChecked(false);
                    Toast.makeText(requireContext(), R.string.notifications_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        c = NewsHubApp.container(requireContext());
        getPreferenceManager().setPreferenceDataStore(new RoomPreferenceDataStore(c.settings));
        setPreferencesFromResource(R.xml.preferences, rootKey);

        PreferenceCategory dev = findPreference("dev_group");
        if (dev != null) dev.setVisible(DevTools.isAvailable());

        Preference theme = findPreference(SettingsRepository.THEME);
        if (theme != null) {
            theme.setOnPreferenceChangeListener((p, value) -> {
                ThemeHelper.apply(String.valueOf(value));
                return true;
            });
        }

        Preference notifications = findPreference(SettingsRepository.NOTIFICATIONS);
        if (notifications != null) {
            notifications.setOnPreferenceChangeListener((p, value) -> {
                if (Boolean.TRUE.equals(value) && Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
                return true;
            });
        }

        Preference demo = findPreference(SettingsRepository.DEMO);
        if (demo != null) {
            demo.setOnPreferenceChangeListener((p, value) -> {
                DevTools.setDemoEnabled(c, Boolean.TRUE.equals(value), null);
                return true;
            });
        }

        onClick("politics_hidden", () -> NavHostFragment.findNavController(this).navigate(R.id.hiddenFragment));
        onClick("politics_rules", () -> PoliticsRulesDialog.show(requireContext(), c.settings));
        onClick("interests", () -> InterestsDialog.show(requireContext(), c.settings, null));
        onClick("clear_history", () -> confirm(R.string.history_clear, R.string.history_clear_confirm,
                () -> c.news.clearHistory(null)));
        onClick("clear_cache", () -> confirm(R.string.pref_clear_cache, R.string.clear_cache_confirm,
                () -> c.news.clearCache(null)));
        onClick("privacy", () -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_privacy)
                .setMessage(R.string.privacy_text)
                .setPositiveButton(R.string.action_close, null)
                .show());
        Preference about = findPreference("about");
        if (about != null) {
            about.setSummary(getString(R.string.pref_about_summary, BuildConfig.VERSION_NAME));
            about.setOnPreferenceClickListener(p -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.app_name)
                        .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME))
                        .setPositiveButton(R.string.action_close, null)
                        .show();
                return true;
            });
        }
    }

    private void onClick(String key, Runnable action) {
        Preference p = findPreference(key);
        if (p != null) {
            p.setOnPreferenceClickListener(pref -> {
                action.run();
                return true;
            });
        }
    }

    private void confirm(int title, int message, Runnable action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_confirm, (d, w) -> action.run())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
