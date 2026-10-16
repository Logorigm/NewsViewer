package com.newshub.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.newshub.app.R;

/** Settings tab: a toolbar plus the preference list. */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getChildFragmentManager().findFragmentById(R.id.settings_container) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_container, new SettingsPrefsFragment())
                    .commit();
        }
    }
}
