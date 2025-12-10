package de.swiftbyte.gmc.daemon.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

public final class Utils {

    private static JsonMapper mapper;

    private Utils() {
    }

    /**
     * Checks if an object is {@code null} or its string representation is empty.
     *
     * @param obj object to check
     * @return {@code true} if the object is {@code null} or empty
     */
    public static boolean isNullOrEmpty(@Nullable Object obj) {

        if (obj == null) {
            return true;
        }

        return obj.toString().isEmpty();
    }

    /**
     * Returns the provided value or a default if the value is {@code null}.
     *
     * @param value         value that may be {@code null}
     * @param defaultValue  fallback value to use
     * @param <T>           value type
     * @return {@code value} when non-null, otherwise {@code defaultValue}
     */
    public static <T> @NonNull T valueOrDefault(@Nullable T value, @NonNull T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * Lazily creates a shared {@link ObjectReader} for the given target class.
     *
     * @param clazz type to deserialize into
     * @return configured {@link ObjectReader} for {@code clazz}
     */
    public static @NonNull ObjectReader getObjectReader(Class<?> clazz) {

        if (mapper == null) {
            mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
        }

        return mapper.readerFor(clazz);
    }
}
