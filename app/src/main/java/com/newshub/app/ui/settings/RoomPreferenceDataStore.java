package com.newshub.app.ui.settings;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.newshub.app.data.repo.SettingsRepository;

import java.util.Set;

/** Lets the standard preference screen read and write settings stored in Room. */
public class RoomPreferenceDataStore extends PreferenceDataStore {

    private final SettingsRepository settings;

    public RoomPreferenceDataStore(SettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public void putString(String key, @Nullable String value) {
        settings.putString(key, value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return settings.getString(key, defValue);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        settings.putBoolean(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return settings.getBoolean(key, defValue);
    }

    @Override
    public void putInt(String key, int value) {
        settings.putString(key, Integer.toString(value));
    }

    @Override
    public int getInt(String key, int defValue) {
        return settings.getInt(key, defValue);
    }

    @Override
    public void putStringSet(String key, @Nullable Set<String> values) {
        settings.putStringSet(key, values);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return settings.getStringSet(key, defValues);
    }
}
