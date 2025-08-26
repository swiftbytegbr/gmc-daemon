package de.swiftbyte.gmc.utils.settings;

import de.swiftbyte.gmc.utils.CommonUtils;

import java.util.HashMap;
import java.util.Map;

public class MapSettingsAdapter {

    private final Map<String, Object> settings;

    public MapSettingsAdapter(Map<String, Object> settings) {
        this.settings = settings;
    }

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
        if (!hasAndNotEmpty(key)) return defaultValue;
        return get(key);
    }

    public Integer getInt(String key, int defaultValue) {
        if (!hasAndNotEmpty(key)) return defaultValue;
        return getInt(key);
    }

    public Boolean getBoolean(String key, boolean defaultValue) {
        if (!hasAndNotEmpty(key)) return defaultValue;
        return getBoolean(key);
    }

    public boolean has(String key) {
        return settings.containsKey(key);
    }

    public boolean hasAndNotEmpty(String key) {
        return has(key) && !CommonUtils.isNullOrEmpty(settings.get(key).toString());
    }

    public static boolean isRestartOnCrash(HashMap<String, Object> gmcSettings) {
        if(gmcSettings == null) return false;
        if(gmcSettings.containsKey("RestartOnCrash")) {
            if(gmcSettings.get("RestartOnCrash") instanceof Boolean) {
                return (boolean) gmcSettings.get("RestartOnCrash");
            }
            return false;
        }
        return false;
    }

    public static boolean isStartOnBoot(HashMap<String, Object> gmcSettings) {
        if(gmcSettings == null) return false;
        if(gmcSettings.containsKey("StartOnBoot")) {
            if(gmcSettings.get("StartOnBoot") instanceof Boolean) {
                return (boolean) gmcSettings.get("StartOnBoot");
            }
            return false;
        }
        return false;
    }
}
