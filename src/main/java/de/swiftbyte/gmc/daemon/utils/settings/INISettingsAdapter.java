package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;

public record INISettingsAdapter(
        LinkedHashMap<@NonNull String, @Nullable LinkedHashMap<@NonNull String, @Nullable Object>> settings) {

    public @Nullable String get(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? value.toString() : null;
    }

    public @Nullable Integer getInt(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? (int) value : null;
    }

    public @Nullable Boolean getBoolean(@NonNull String category, @NonNull String key) {
        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? (Boolean) value : null;
    }

    public @NonNull String get(@NonNull String category, @NonNull String key, @NonNull String defaultValue) {
        String value = get(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public @NonNull Integer getInt(@NonNull String category, @NonNull String key, int defaultValue) {
        Integer value = getInt(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public @NonNull Boolean getBoolean(@NonNull String category, @NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public boolean has(String category, String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return false;
        }
        return cat.containsKey(key);
    }

    public boolean hasAndNotEmpty(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return false;
        }
        return !Utils.isNullOrEmpty(cat.get(key));
    }
}
