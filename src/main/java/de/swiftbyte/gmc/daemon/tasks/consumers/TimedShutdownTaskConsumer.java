package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import de.swiftbyte.gmc.daemon.utils.TimedMessageUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TimedShutdownTaskConsumer implements NodeTaskConsumer {

    private static final ConcurrentHashMap<String, AtomicBoolean> CANCEL_FLAGS = new ConcurrentHashMap<>();

    @Override
    public void run(NodeTask task, Object payload) {

        if(!(payload instanceof TimedShutdownPayload p)) {
            throw new IllegalArgumentException("Expected TimedShutdownPayload");
        }

        GameServer server = GameServer.getServerById(p.serverId());
        if (server == null) {
            log.warn("Timed shutdown: server {} not found", p.serverId());
            return;
        }

        int minutesLeft = Math.max(0, p.delayMinutes());

        boolean shouldBeCancellable = minutesLeft > 1;
        if (task.isCancellable() != shouldBeCancellable) {
            task.setCancellable(shouldBeCancellable);
            TaskService.updateTask(task);
        }

        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        CANCEL_FLAGS.put(task.getId(), cancelFlag);
        try {
            MapSettingsAdapter gmc = new MapSettingsAdapter(server.getSettings().getGmcSettings());
            String baseMessage = CommonUtils.isNullOrEmpty(p.message())
                    ? gmc.get("DefaultDelayedShutdownMessage", null)
                    : p.message();
            List<Integer> milestones = TimedMessageUtils.getMessageMilestoneList(gmc);
            if (!CommonUtils.isNullOrEmpty(baseMessage) && minutesLeft > 0) {
                sendMessage(server, baseMessage, minutesLeft);
            }

            while (minutesLeft > 0) {
                if (Thread.currentThread().isInterrupted() || cancelFlag.get()) {
                    log.debug("Timed shutdown for server {} canceled during countdown", p.serverId());
                    return;
                }
                sleepOneMinuteInterruptibly();
                minutesLeft--;
                if (minutesLeft == 1 && task.isCancellable()) {
                    task.setCancellable(false);
                    TaskService.updateTask(task);
                }
                if (!CommonUtils.isNullOrEmpty(baseMessage) && minutesLeft > 0 && !milestones.isEmpty() && milestones.contains(minutesLeft)) {
                    sendMessage(server, baseMessage, minutesLeft);
                }
            }

            // Final cancellation check just before executing the action
            if (Thread.currentThread().isInterrupted() || cancelFlag.get()) {
                log.debug("Timed shutdown for server {} canceled right before execution", p.serverId());
                task.setState(NodeTask.State.CANCELED);
                TaskService.updateTask(task);
                return;
            }

            log.info("Executing timed shutdown for server {} (force: {})", p.serverId(), p.forceStop());
            server.stop(false, p.forceStop()).complete();

        } finally {
            CANCEL_FLAGS.remove(task.getId());
        }
    }

    private void sleepOneMinuteInterruptibly() {
        long millis = TimeUnit.MINUTES.toMillis(1);
        long end = System.currentTimeMillis() + millis;
        while (true) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) break;
            try {
                Thread.sleep(Math.min(remaining, 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendMessage(GameServer server, String template, int minutesLeft) {
        String msg = template.replace("{minutes}", String.valueOf(minutesLeft));
        log.debug("Sending timed shutdown message for server {}: {}", server.getFriendlyName(), msg);
        server.sendRconCommand("serverchat " + msg);
    }

    

    @Override
    public void cancel(NodeTask task) {
        AtomicBoolean flag = CANCEL_FLAGS.get(task.getId());
        if (flag != null) {
            flag.set(true);
        }
    }

    @Override
    public boolean isCancellable(Object payload) {
        if (payload instanceof TimedShutdownPayload p) {
            return p.delayMinutes() > 1;
        }
        return false;
    }

    public record TimedShutdownPayload(String serverId, int delayMinutes, boolean forceStop, String message) {}
}
