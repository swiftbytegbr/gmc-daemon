package de.swiftbyte.gmc.daemon.utils.action;

import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public interface AsyncAction<T> {

    default void queue() {
        queue(null);
    }

    default void queue(Consumer<? super T> success) {
        queue(success, null);
    }

    default void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        Thread thread = new Thread(() -> {
            T action = complete();
            if (action != null && success != null) success.accept(action);
        });
        thread.setUncaughtExceptionHandler((t, e) -> {
            if (failure != null) failure.accept(e);
            else {
                LoggerFactory.getLogger(AsyncAction.class).error("An unknown error occurred while executing.", e);
            }
        });
        thread.start();
    }

    T complete();

}
