package de.swiftbyte.gmc.daemon.utils.settings;

import de.swiftbyte.gmc.daemon.utils.Utils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;

public record INISettingsAdapter(
        LinkedHashMap<@NonNull String, @Nullable LinkedHashMap<@NonNull String, @Nullable Object>> settings) {

    /**
     * Returns the value for the given category/key as a string.
     *
     * @param category section name
     * @param key      entry key
     * @return string value or {@code null} if missing
     */
    public @Nullable String get(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Returns the value for the given category/key as an integer.
     *
     * @param category section name
     * @param key      entry key
     * @return integer value or {@code null} if missing
     */
    public @Nullable Integer getInt(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? (int) value : null;
    }

    /**
     * Returns the value for the given category/key as a boolean.
     *
     * @param category section name
     * @param key      entry key
     * @return boolean value or {@code null} if missing
     */
    public @Nullable Boolean getBoolean(@NonNull String category, @NonNull String key) {
        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return null;
        }

        Object value = cat.get(key);
        return value != null ? (Boolean) value : null;
    }

    /**
     * Returns the string value for the given category/key or a fallback.
     *
     * @param category     section name
     * @param key          entry key
     * @param defaultValue value to return when empty or missing
     * @return stored value or {@code defaultValue}
     */
    public @NonNull String get(@NonNull String category, @NonNull String key, @NonNull String defaultValue) {
        String value = get(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Returns the integer value for the given category/key or a fallback.
     *
     * @param category     section name
     * @param key          entry key
     * @param defaultValue value to return when empty or missing
     * @return stored value or {@code defaultValue}
     */
    public @NonNull Integer getInt(@NonNull String category, @NonNull String key, int defaultValue) {
        Integer value = getInt(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Returns the boolean value for the given category/key or a fallback.
     *
     * @param category     section name
     * @param key          entry key
     * @param defaultValue value to return when empty or missing
     * @return stored value or {@code defaultValue}
     */
    public @NonNull Boolean getBoolean(@NonNull String category, @NonNull String key, boolean defaultValue) {
        Boolean value = getBoolean(category, key);
        return Utils.isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Checks whether a category contains the given key.
     *
     * @param category section name
     * @param key      entry key
     * @return {@code true} when the key exists
     */
    public boolean has(String category, String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return false;
        }
        return cat.containsKey(key);
    }

    /**
     * Checks whether a category contains a non-empty value for the given key.
     *
     * @param category section name
     * @param key      entry key
     * @return {@code true} when the key exists and has a non-empty value
     */
    public boolean hasAndNotEmpty(@NonNull String category, @NonNull String key) {

        LinkedHashMap<String, Object> cat = settings.get(category);

        if (cat == null) {
            return false;
        }
        return !Utils.isNullOrEmpty(cat.get(key));
    }
}
