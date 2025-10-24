package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.CommonUtils;

import java.util.LinkedHashMap;

public class INISettingsAdapter {

    private final LinkedHashMap<String, LinkedHashMap<String, Object>> settings;

    public INISettingsAdapter(LinkedHashMap<String, LinkedHashMap<String, Object>> settings) {
        this.settings = settings;
    }

    public String get(String category, String key) {
        if (!settings.containsKey(category)) {
            return null;
        }
        Object value = settings.get(category).get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String category, String key) {
        if (!settings.containsKey(category)) {
            return null;
        }
        Object value = settings.get(category).get(key);
        return value != null ? (int) value : null;
    }

    public Boolean getBoolean(String category, String key) {
        if (!settings.containsKey(category)) {
            return null;
        }
        Object value = settings.get(category).get(key);
        return value != null ? (Boolean) value : null;
    }

    public String get(String category, String key, String defaultValue) {
        if (!hasAndNotEmpty(category, key)) {
            return defaultValue;
        }
        return get(category, key);
    }

    public Integer getInt(String category, String key, int defaultValue) {
        if (!hasAndNotEmpty(category, key)) {
            return defaultValue;
        }
        return getInt(category, key);
    }

    public Boolean getBoolean(String category, String key, boolean defaultValue) {
        if (!hasAndNotEmpty(category, key)) {
            return defaultValue;
        }
        return getBoolean(category, key);
    }

    public boolean has(String category, String key) {
        if (!settings.containsKey(category)) {
            return false;
        }
        return settings.get(category).containsKey(key);
    }

    public boolean hasAndNotEmpty(String category, String key) {
        if (!settings.containsKey(category)) {
            return false;
        }
        return has(category, key) && !CommonUtils.isNullOrEmpty(settings.get(category).get(key).toString());
    }
}
