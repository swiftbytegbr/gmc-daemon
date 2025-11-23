package de.swiftbyte.gmc.daemon.service;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeTaskCompletePacket;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeTaskCreatePacket;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeTaskUpdatePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.BackupTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.BackupDirectoryChangeTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.TimedRestartTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.ServerDirectoryChangeTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.RollbackTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.TimedShutdownTaskConsumer;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class TaskService {

    private static final HashMap<NodeTask.Type, NodeTaskConsumer> CONSUMERS = new HashMap<>();
    private static final ConcurrentHashMap<String, TaskRun> TASKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> LAST_PROGRESS = new ConcurrentHashMap<>();

    private static ExecutorService executor = null;

    public static void initializeTaskService() {

        shutdownTaskService();

        executor = Executors.newCachedThreadPool();

        registerConsumer(NodeTask.Type.BACKUP, new BackupTaskConsumer());
        registerConsumer(NodeTask.Type.ROLLBACK, new RollbackTaskConsumer());
        registerConsumer(NodeTask.Type.BACKUP_DIRECTORY_CHANGE, new BackupDirectoryChangeTaskConsumer());
        registerConsumer(NodeTask.Type.SERVER_DIRECTORY_CHANGE, new ServerDirectoryChangeTaskConsumer());

        registerConsumer(NodeTask.Type.TIMED_SHUTDOWN, new TimedShutdownTaskConsumer());
        registerConsumer(NodeTask.Type.TIMED_RESTART, new TimedRestartTaskConsumer());

    }

    public static void shutdownTaskService() {

        TASKS.forEach((_, taskRun) -> {
            // Only attempt to cancel if the task is currently marked cancellable
            if (taskRun.task.isCancellable()) {
                cancelTask(taskRun.task.getId());
            }
        });

        if(executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        CONSUMERS.clear();
    }

    public static boolean createTask(NodeTask.Type type, String nodeId, String...targetIds) {
        return createTask(type, null, nodeId, targetIds);
    }

    public static boolean createTask(NodeTask.Type type, Object payload, String nodeId, String...affectedIds) {
        return createTask(type, payload, nodeId, null, affectedIds);
    }

    public static boolean createTask(NodeTask.Type type, Object payload, String nodeId, HashMap<String, Object> context, String...affectedIds) {

        NodeTaskConsumer consumer = CONSUMERS.get(type);
        if (consumer == null) {
            log.warn("No consumer registered for task type {}. Skipping creation.", type);
            return false;
        }

        int maxTasks = consumer.maxConcurrentTasks();
        if (maxTasks > 0) {
            long runningOfType = TASKS.values().stream()
                    .map(tr -> tr.task)
                    .filter(t -> t.getType() == type)
                    .filter(t -> t.getState() == NodeTask.State.RUNNING)
                    .count();
            if (runningOfType >= maxTasks) {
                log.debug("Max concurrent tasks for type {} reached ({}). Skipping creation.", type, maxTasks);
                return false;
            }
        }

        // Build task
        NodeTask task = new NodeTask();
        task.setId(UUID.randomUUID().toString());
        task.setType(type);
        task.setCreatedAt(Instant.now());
        task.setState(NodeTask.State.PENDING);
        task.setNodeId(nodeId);
        task.setContext(context);
        task.setTargetIds(Arrays.asList(affectedIds == null ? new String[]{} : affectedIds));
        task.setCancellable(consumer.isCancellable(payload));

        String targetsStr = Arrays.toString(affectedIds == null ? new String[]{} : affectedIds);
        log.debug("Creating task: id={}, type={}, nodeId={}, targets={}, initialCancellable={}",
                task.getId(), type, nodeId, targetsStr, task.isCancellable());

        // Publish creation
        NodeTaskCreatePacket packet = new NodeTaskCreatePacket();
        packet.setNodeTask(task);
        StompHandler.send("/app/node/task-create", packet);

        // Place a placeholder run entry first to allow immediate soft-cancel
        TASKS.put(task.getId(), new TaskRun(null, task, payload));

        // Submit execution
        Future<?> future = executor.submit(() -> runTask(task, payload, consumer));

        // Update entry with real future
        TASKS.put(task.getId(), new TaskRun(future, task, payload));
        return true;
    }

    private static void runTask(NodeTask task, Object payload, NodeTaskConsumer consumer) {
        try {
            task.setState(NodeTask.State.RUNNING);
            sendUpdatePacket(task);
            log.debug("Task started: id={}, type={}", task.getId(), task.getType());

            consumer.run(task, payload);

            // If consumer didn't set a terminal state, default to SUCCEEDED
            if (task.getState() == null || task.getState() == NodeTask.State.RUNNING || task.getState() == NodeTask.State.PENDING) {
                task.setState(NodeTask.State.SUCCEEDED);
            }

            // If someone (e.g., hard cancel) already finalized the task, skip sending completion
            if (task.getFinishedAt() == null) {
                task.setFinishedAt(Instant.now());
                NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
                completePacket.setNodeTask(task);
                StompHandler.send("/app/node/task-complete", completePacket);
                log.debug("Task completed: id={}, type={}, finalState={}", task.getId(), task.getType(), task.getState());
            } else {
                log.debug("Task {} already finalized (state={}), skipping completion send.", task.getId(), task.getState());
            }

        } catch (Exception e) {
            // If the task was already finalized (e.g., via hard cancel), do not send another completion
            if (task.getFinishedAt() != null) {
                log.debug("Task {} threw after finalization; skipping completion (state={}).", task.getId(), task.getState());
            } else {
                boolean interrupted = Thread.currentThread().isInterrupted() || (e instanceof InterruptedException);
                if (interrupted) {
                    task.setState(NodeTask.State.CANCELED);
                    task.setErrorMessage(null);
                    task.setFinishedAt(Instant.now());
                    NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
                    completePacket.setNodeTask(task);
                    StompHandler.send("/app/node/task-complete", completePacket);
                    log.debug("Task canceled via interruption: id={}, type={}", task.getId(), task.getType());
                } else {
                    log.error("An unhandled exception occurred while executing task {}", task.getType(), e);
                    task.setState(NodeTask.State.FAILED);
                    task.setErrorMessage(e.getMessage());
                    task.setFinishedAt(Instant.now());
                    NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
                    completePacket.setNodeTask(task);
                    StompHandler.send("/app/node/task-complete", completePacket);
                    log.debug("Task failed: id={}, type={}", task.getId(), task.getType());
                }
            }
        }

        TASKS.remove(task.getId());
        LAST_PROGRESS.remove(task.getId());
    }


    public static boolean cancelTask(String taskId) {

        TaskRun taskRun = TASKS.get(taskId);
        if (taskRun == null) {
            log.warn("Tried to cancel non-existing task {}", taskId);
            return false;
        }

        NodeTask task = taskRun.task;

        // Only perform hard-cancel if task is currently cancellable
        if (!task.isCancellable()) {
            log.warn("Skip cancellation for task {}: task is no longer cancellable.", taskId);
            return false;
        }

        try {
            // Interrupt the running task thread only; let runTask handle completion
            if (taskRun.future != null) {
                taskRun.future.cancel(true);
            }
            log.debug("Interrupt requested for task {}", taskId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to interrupt task {} due to exception.", taskId, e);
            return false;
        }
    }

    private static void sendUpdatePacket(NodeTask task) {
        NodeTaskUpdatePacket packet = new NodeTaskUpdatePacket();
        packet.setNodeTask(task);

        StompHandler.send("/app/node/task-update", packet);
        log.debug("Task update sent: id={}, state={}, cancellable={}", task.getId(), task.getState(), task.isCancellable());
    }

    public static void updateTask(NodeTask task) {
        sendUpdatePacket(task);
    }

    public static void updateTaskProgress(NodeTask task, int percent) {
        if (task == null) return;
        if (task.getType() != NodeTask.Type.SERVER_DIRECTORY_CHANGE) return;

        int clamped = Math.max(0, Math.min(100, percent));
        Integer last = LAST_PROGRESS.get(task.getId());
        boolean initial = last == null;
        boolean threshold = !initial && Math.abs(clamped - last) > 5;
        boolean force = clamped == 100 || clamped == 0;

        if (initial || threshold || force) {
            task.setProgressPercentage(clamped);
            LAST_PROGRESS.put(task.getId(), clamped);
            sendUpdatePacket(task);
            log.debug("Task progress update: id={}, type={}, progress={}%, lastSent={}", task.getId(), task.getType(), clamped, last);
        }
    }


    private static void registerConsumer(NodeTask.Type type, NodeTaskConsumer consumer) {
        CONSUMERS.put(type, consumer);
    }

    private record TaskRun(
            Future<?> future,
            NodeTask task,
            Object payload
    ) {}
}
