package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record MapSettingsAdapter(@NonNull Map<@NonNull String, @Nullable Object> settings) {

    public @Nullable String get(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? value.toString() : null;
    }

    public @Nullable Integer getInt(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? (int) value : null;
    }

    public @Nullable Boolean getBoolean(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? (Boolean) value : null;
    }

    public @NonNull String get(@NonNull String key, @NonNull String defaultValue) {
        String value = get(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public @NonNull Integer getInt(@NonNull String key, int defaultValue) {
        Integer value = getInt(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public @NonNull Boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    public boolean has(@NonNull String key) {
        return settings.get(key) != null;
    }

    public boolean hasAndNotEmpty(@NonNull String key) {
        return has(key) && !Utils.isNullOrEmpty(settings.get(key));
    }
}
