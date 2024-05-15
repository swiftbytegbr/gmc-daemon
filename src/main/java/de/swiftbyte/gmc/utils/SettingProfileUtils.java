package de.swiftbyte.gmc.utils;

import java.util.LinkedHashMap;

public class SettingProfileUtils {

    private LinkedHashMap<String, LinkedHashMap<String, Object>> settings;

    public SettingProfileUtils(LinkedHashMap<String, LinkedHashMap<String, Object>> settings) {
        this.settings = settings;
    }

    public String getSetting(String category, String key) {
        if(!settings.containsKey(category)) return null;
        Object value = settings.get(category).get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getSettingAsInt(String category, String key) {
        if(!settings.containsKey(category)) return null;
        Object value = settings.get(category).get(key);
        return value != null ? (int) value : null;
    }

    public Boolean getSettingAsBoolean(String category, String key) {
        if(!settings.containsKey(category)) return null;
        Object value = settings.get(category).get(key);
        return value != null ? (Boolean) value : null;
    }

    public String getSetting(String category, String key, String defaultValue) {
        if(!hasSettingAndNotEmpty(category, key)) return defaultValue;
        return getSetting(category, key);
    }

    public Integer getSettingAsInt(String category, String key, int defaultValue) {
        if(!hasSettingAndNotEmpty(category, key)) return defaultValue;
        return getSettingAsInt(category, key);
    }

    public Boolean getSettingAsBoolean(String category, String key, boolean defaultValue) {
        if(!hasSettingAndNotEmpty(category, key)) return defaultValue;
        return getSettingAsBoolean(category, key);
    }

    public boolean hasSetting(String category, String key) {
        if(!settings.containsKey(category)) return false;
        return settings.get(category).containsKey(key);
    }

    public boolean hasSettingAndNotEmpty(String category, String key) {
        if(!settings.containsKey(category)) return false;
        return hasSetting(category, key) && !CommonUtils.isNullOrEmpty(settings.get(category).get(key).toString());
    }
}
