package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.TimedMessageUtils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TimedRestartTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {

        if (!(payload instanceof TimedRestartPayload p)) {
            throw new IllegalArgumentException("Expected TimedRestartPayload");
        }

        GameServer server = GameServer.getServerById(p.serverId());
        if (server == null) {
            log.warn("Timed restart: server {} not found", p.serverId());
            return;
        }

        int minutesLeft = Math.max(0, p.delayMinutes());

        task.setCancellable(true);
        TaskService.updateTask(task);

        try {
            MapSettingsAdapter gmc = new MapSettingsAdapter(server.getSettings().getGmcSettings());
            String baseMessage = CommonUtils.isNullOrEmpty(p.message())
                    ? gmc.get("DefaultDelayedRestartMessage", null)
                    : p.message();
            java.util.List<Integer> milestones = TimedMessageUtils.getMessageMilestoneList(gmc);
            if (!CommonUtils.isNullOrEmpty(baseMessage) && minutesLeft > 0) {
                sendMessage(server, baseMessage, minutesLeft);
            }

            while (minutesLeft > 0) {
                if (Thread.currentThread().isInterrupted()) {
                    log.debug("Timed restart for server {} canceled during countdown", p.serverId());
                    task.setState(NodeTask.State.CANCELED);
                    return;
                }
                sleepOneMinuteInterruptibly();
                minutesLeft--;
                if (!CommonUtils.isNullOrEmpty(baseMessage) && minutesLeft > 0 && !milestones.isEmpty() && milestones.contains(minutesLeft)) {
                    sendMessage(server, baseMessage, minutesLeft);
                }
            }

            // Final cancellation check just before executing the action
            if (Thread.currentThread().isInterrupted()) {
                log.debug("Timed restart for server {} canceled right before execution", p.serverId());
                // Consumer sets state, TaskService sends completion
                task.setState(NodeTask.State.CANCELED);
                return;
            }

            log.info("Executing timed restart for server {}", p.serverId());
            server.restart().queue();

        } finally {
            // no-op
        }
    }

    private void sleepOneMinuteInterruptibly() {
        long millis = TimeUnit.MINUTES.toMillis(1);
        long end = System.currentTimeMillis() + millis;
        while (true) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
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
        log.debug("Sending timed restart message for server {}: {}", server.getFriendlyName(), msg);
        server.sendRconCommand("serverchat " + msg);
    }

    @Override
    public boolean isCancellable(Object payload) {
        if (payload instanceof TimedRestartPayload p) {
            return p.delayMinutes() > 1;
        }
        return false;
    }


    public record TimedRestartPayload(String serverId, int delayMinutes, String message) {
    }
}
