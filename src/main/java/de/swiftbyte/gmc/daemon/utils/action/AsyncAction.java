package de.swiftbyte.gmc.daemon.utils.action;

import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public interface AsyncAction<T> {

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    default void queue() {
        queue(null);
    }

    default void queue(Consumer<? super T> success) {
        queue(success, null);
    }

    default void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(this::complete, executor);

        if(success != null) future.thenAccept(success);
        future.exceptionally(ex -> {
            if(failure != null) failure.accept(ex);
            else LoggerFactory.getLogger(getClass()).error("An unknown error occurred while executing.", ex);
            return null;
        });
    }

    T complete();

}
