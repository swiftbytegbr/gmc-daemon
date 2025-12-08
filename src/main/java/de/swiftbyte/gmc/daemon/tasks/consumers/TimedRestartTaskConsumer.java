package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.TimedPowerActionsUtils;
import de.swiftbyte.gmc.daemon.utils.Utils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@CustomLog
public class TimedRestartTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(@NonNull NodeTask task, @Nullable Object payload) {

        if (!(payload instanceof TimedRestartPayload(String serverId, int delayMinutes, String message))) {
            throw new IllegalArgumentException("Expected TimedRestartPayload");
        }

        GameServer server = GameServer.getServerById(serverId);
        if (server == null) {
            log.warn("Timed restart: server {} not found", serverId);
            return;
        }

        int minutesLeft = Math.max(0, delayMinutes);

        task.setCancellable(true);
        TaskService.updateTask(task);

        MapSettingsAdapter gmc = new MapSettingsAdapter(server.getSettings().getGmcSettings());
        String baseMessage = Utils.isNullOrEmpty(message)
                ? gmc.get("DefaultDelayedRestartMessage")
                : message;
        java.util.List<Integer> milestones = TimedPowerActionsUtils.getMessageMilestoneList(gmc);
        if (!Utils.isNullOrEmpty(baseMessage) && minutesLeft > 0) {
            sendMessage(server, baseMessage, minutesLeft);
        }

        while (minutesLeft > 0) {
            if (Thread.currentThread().isInterrupted()) {
                log.debug("Timed restart for server {} canceled during countdown", serverId);
                task.setState(NodeTask.State.CANCELED);
                return;
            }
            TimedPowerActionsUtils.sleepInterruptibly();
            minutesLeft--;
            if (!Utils.isNullOrEmpty(baseMessage) && minutesLeft > 0 && !milestones.isEmpty() && milestones.contains(minutesLeft)) {
                sendMessage(server, baseMessage, minutesLeft);
            }
        }

        // Final cancellation check just before executing the action
        if (Thread.currentThread().isInterrupted()) {
            log.debug("Timed restart for server {} canceled right before execution", serverId);
            // Consumer sets state, TaskService sends completion
            task.setState(NodeTask.State.CANCELED);
            return;
        }

        log.info("Executing timed restart for server {}", serverId);
        server.restart().queue();

    }

    private void sendMessage(@NonNull GameServer server, @NonNull String template, int minutesLeft) {
        String msg = template.replace("{minutes}", String.valueOf(minutesLeft));
        log.debug("Sending timed restart message for server {}: {}", server.getFriendlyName(), msg);
        server.sendRconCommand("serverchat " + msg);
    }

    @Override
    public boolean isCancellable(@Nullable Object payload) {
        if (payload instanceof TimedRestartPayload p) {
            return p.delayMinutes() > 1;
        }
        return false;
    }


    public record TimedRestartPayload(@NonNull String serverId, int delayMinutes, @Nullable String message) {
    }
}
