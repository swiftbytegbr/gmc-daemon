package de.swiftbyte.gmc.daemon.tasks;

import de.swiftbyte.gmc.common.model.NodeTask;

public interface NodeTaskConsumer {

    void run(NodeTask task, Object payload);

    default void cancel(NodeTask task) {
        throw new RuntimeException("Cancellation of task type " + task.getType() + " not supported");
    }

    default boolean isCancellable(Object payload) {
        return false;
    }

    default boolean isBlocking() {
        return false;
    }

    default int maxConcurrentTasks() {
        return -1;
    }

}
