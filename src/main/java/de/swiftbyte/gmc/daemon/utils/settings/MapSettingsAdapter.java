package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record MapSettingsAdapter(@NonNull Map<@NonNull String, @Nullable Object> settings) {

    /**
     * Returns a value as string for the provided key.
     *
     * @param key settings key
     * @return value as string or {@code null} if missing
     */
    public @Nullable String get(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Returns a value as integer for the provided key.
     *
     * @param key settings key
     * @return value as integer or {@code null} if missing
     */
    public @Nullable Integer getInt(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? (int) value : null;
    }

    /**
     * Returns a value as boolean for the provided key.
     *
     * @param key settings key
     * @return value as boolean or {@code null} if missing
     */
    public @Nullable Boolean getBoolean(@NonNull String key) {
        Object value = settings.get(key);
        return value != null ? (Boolean) value : null;
    }

    /**
     * Returns a value as string or a fallback when missing or empty.
     *
     * @param key          settings key
     * @param defaultValue fallback value
     * @return stored value or {@code defaultValue}
     */
    public @NonNull String get(@NonNull String key, @NonNull String defaultValue) {
        String value = get(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Returns a value as integer or a fallback when missing or empty.
     *
     * @param key          settings key
     * @param defaultValue fallback value
     * @return stored value or {@code defaultValue}
     */
    public @NonNull Integer getInt(@NonNull String key, int defaultValue) {
        Integer value = getInt(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Returns a value as boolean or a fallback when missing or empty.
     *
     * @param key          settings key
     * @param defaultValue fallback value
     * @return stored value or {@code defaultValue}
     */
    public @NonNull Boolean getBoolean(@NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Checks if a key exists in the settings map.
     *
     * @param key settings key
     * @return {@code true} when present
     */
    public boolean has(@NonNull String key) {
        return settings.get(key) != null;
    }

    /**
     * Checks if a key exists and holds a non-empty value.
     *
     * @param key settings key
     * @return {@code true} when the key is present and non-empty
     */
    public boolean hasAndNotEmpty(@NonNull String key) {
        return has(key) && !Utils.isNullOrEmpty(settings.get(key));
    }
}
