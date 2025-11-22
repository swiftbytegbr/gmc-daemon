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

    private static ExecutorService executor = null;

    public static void initializeTaskService() {

        shutdownTaskService();

        executor = Executors.newCachedThreadPool();

        registerConsumer(NodeTask.Type.BACKUP, new BackupTaskConsumer());
        registerConsumer(NodeTask.Type.ROLLBACK, new RollbackTaskConsumer());
        registerConsumer(NodeTask.Type.BACKUP_DIRECTORY_CHANGE, new BackupDirectoryChangeTaskConsumer());

        registerConsumer(NodeTask.Type.TIMED_SHUTDOWN, new TimedShutdownTaskConsumer());
        registerConsumer(NodeTask.Type.TIMED_RESTART, new TimedRestartTaskConsumer());

    }

    public static void shutdownTaskService() {

        TASKS.forEach((_, taskRun) -> {
            if(CONSUMERS.get(taskRun.task.getType()).isCancellable(taskRun.payload)) {
                cancelTask(taskRun.task.getId(), false);
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

        int maxTasks = CONSUMERS.get(type).maxConcurrentTasks();
        if(maxTasks > 0 && CONSUMERS.keySet().stream().filter(it -> it == type).count() >= maxTasks + 1) {
            log.debug("Max count of concurrent tasks exceeded for type {}. Skipping creation...", type);
            return false;
        }

        NodeTask task = new NodeTask();
        task.setId(UUID.randomUUID().toString());
        task.setType(type);
        task.setCreatedAt(Instant.now());
        task.setState(NodeTask.State.PENDING);
        task.setNodeId(nodeId);
        task.setTargetIds(Arrays.asList(affectedIds));
        task.setCancellable(CONSUMERS.get(type).isCancellable(payload));

        log.debug("Creating task: id={}, type={}, nodeId={}, targets={}, initialCancellable={}",
                task.getId(), type, nodeId, Arrays.toString(affectedIds), task.isCancellable());

        NodeTaskCreatePacket packet = new NodeTaskCreatePacket();
        packet.setNodeTask(task);
        StompHandler.send("/app/node/task-create", packet);
        log.debug("Task create packet sent: id={}", task.getId());

        Future<?> future = executor.submit(() -> {
            try {
                task.setState(NodeTask.State.RUNNING);
                sendUpdatePacket(task);
                log.debug("Task started: id={}, type={}", task.getId(), task.getType());

                CONSUMERS.get(type).run(task, payload);

                NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
                completePacket.setNodeTask(task);
                StompHandler.send("/app/node/task-complete", completePacket);
                log.debug("Task completed: id={}, type={}", task.getId(), task.getType());

            } catch (Exception e) {
                log.error("An unhandled exception occurred while executing task {}", type, e);
                task.setState(NodeTask.State.FAILED);
                NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
                completePacket.setNodeTask(task);
                StompHandler.send("/app/node/task-complete", completePacket);
                log.debug("Task failed: id={}, type={}", task.getId(), task.getType());
            }

            TASKS.remove(task.getId());
        });

        TASKS.put(task.getId(), new TaskRun(future, task, payload));
        return true;
    }

    public static boolean cancelTask(String taskId) {
        return cancelTask(taskId, false);
    }

    public static boolean cancelTask(String taskId, boolean force) {

        if(!TASKS.containsKey(taskId)) {
            log.warn("Tried to cancel non-existing task {}", taskId);
            return false;
        }

        TaskRun taskRun = TASKS.get(taskId);

        if(!taskRun.task.isCancellable() && !force) {
            // Soft-cancel: inform consumer to stop at next safe checkpoint
            try {
                CONSUMERS.get(taskRun.task.getType()).cancel(taskRun.task);
                log.debug("Accepted late cancellation for task {} (soft cancel)", taskId);
                return true;
            } catch (Exception e) {
                log.warn("Late cancellation not supported by consumer for task {}", taskId);
                return false;
            }
        }

        taskRun.future.cancel(true);
        TASKS.remove(taskId);

        CONSUMERS.get(taskRun.task.getType()).cancel(taskRun.task);
        taskRun.task.setState(NodeTask.State.CANCELED);
        // Inform backend about cancellation and completion (so it can remove stored task)
        sendUpdatePacket(taskRun.task);
        NodeTaskCompletePacket completePacket = new NodeTaskCompletePacket();
        completePacket.setNodeTask(taskRun.task);
        StompHandler.send("/app/node/task-complete", completePacket);
        log.debug("Task canceled: id={}, type={}", taskRun.task.getId(), taskRun.task.getType());

        return true;
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


    private static void registerConsumer(NodeTask.Type type, NodeTaskConsumer consumer) {
        CONSUMERS.put(type, consumer);
    }

    private record TaskRun(
            Future<?> future,
            NodeTask task,
            Object payload
    ) {}
}
