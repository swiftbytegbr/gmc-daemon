package de.swiftbyte.gmc.daemon.logging;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Central place for Lombok {@code @CustomLog} to obtain loggers with non-null contract.
 */
public final class LoggerProvider {

    private LoggerProvider() {
        // utility
    }

    @NotNull
    public static Logger getLogger(@NotNull Class<?> type) {
        return Objects.requireNonNull(LoggerFactory.getLogger(type));
    }
}
