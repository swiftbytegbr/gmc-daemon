package de.swiftbyte.gmc.daemon.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

public final class Utils {

    private static JsonMapper mapper;

    private Utils() {
    }

    public static boolean isNullOrEmpty(@Nullable Object obj) {

        if (obj == null) {
            return true;
        }

        return obj.toString().isEmpty();
    }

    public static <T> @NonNull T valueOrDefault(@Nullable T value, @NonNull T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static @NonNull ObjectReader getObjectReader(Class<?> clazz) {

        if (mapper == null) {
            mapper = JsonMapper.builder().build();
        }

        return mapper.readerFor(clazz);
    }
}
