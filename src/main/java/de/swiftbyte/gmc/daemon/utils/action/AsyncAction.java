package de.swiftbyte.gmc.daemon.utils.action;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public interface AsyncAction<T> {

    @NonNull ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Queues the action for asynchronous execution without callbacks.
     */
    default void queue() {
        queue(null);
    }

    /**
     * Queues the action for asynchronous execution with a success callback.
     *
     * @param success consumer invoked with the result on success
     */
    default void queue(@Nullable Consumer<? super T> success) {
        queue(success, null);
    }

    /**
     * Queues the action for asynchronous execution with success and failure callbacks.
     *
     * @param success consumer invoked with the result on success
     * @param failure consumer invoked with the throwable on failure
     */
    default void queue(@Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure) {

        CompletableFuture<T> future = CompletableFuture.supplyAsync(this::complete, executor);

        if (success != null) {
            future.thenAccept(success);
        }

        future.exceptionally(ex -> {
            if (failure != null) {
                failure.accept(ex);
            } else {
                LoggerFactory.getLogger(getClass()).error("An unknown error occurred while executing.", ex);
            }
            return null;
        });
    }

    /**
     * Performs the action synchronously and returns its result.
     *
     * @return result of the action
     */
    T complete();
}
