package de.swiftbyte.gmc.daemon.tasks;

import de.swiftbyte.gmc.common.model.NodeTask;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface NodeTaskConsumer {

    void run(@NonNull NodeTask task, @Nullable Object payload);

    default void cancel(@NonNull NodeTask task) {
        throw new RuntimeException("Cancellation of task type " + task.getType() + " not supported");
    }

    default boolean isCancellable(@Nullable Object payload) {
        return false;
    }

    default int maxConcurrentTasks() {
        return -1;
    }

}
