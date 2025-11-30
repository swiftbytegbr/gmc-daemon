package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.CommonUtils;

import java.util.Map;

public record MapSettingsAdapter(Map<String, Object> settings) {

    public String get(String key) {
        Object value = settings.get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getInt(String key) {
        Object value = settings.get(key);
        return value != null ? (int) value : null;
    }

    public Boolean getBoolean(String key) {
        Object value = settings.get(key);
        return value != null ? (Boolean) value : null;
    }

    public String get(String key, String defaultValue) {
        if (!hasAndNotEmpty(key)) {
            return defaultValue;
        }
        return get(key);
    }

    public Integer getInt(String key, int defaultValue) {
        if (!hasAndNotEmpty(key)) {
            return defaultValue;
        }
        return getInt(key);
    }

    public Boolean getBoolean(String key, boolean defaultValue) {
        if (!hasAndNotEmpty(key)) {
            return defaultValue;
        }
        return getBoolean(key);
    }

    public boolean has(String key) {
        return settings.get(key) != null;
    }

    public boolean hasAndNotEmpty(String key) {
        return has(key) && !CommonUtils.isNullOrEmpty(settings.get(key));
    }
}
