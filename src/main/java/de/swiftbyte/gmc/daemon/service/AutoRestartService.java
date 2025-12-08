package de.swiftbyte.gmc.daemon.service;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.utils.Utils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.cronutils.model.CronType.QUARTZ;
import static com.cronutils.model.CronType.UNIX;

@CustomLog
public class AutoRestartService {

    private static final @NonNull CronParser UNIX_CRON = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(UNIX));
    private static final @NonNull CronParser QUARTZ_CRON = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));

    private static final @NonNull ConcurrentHashMap<@NonNull String, @NonNull ScheduledFuture<?>> RESTART_SCHEDULES = new ConcurrentHashMap<>();

    public static void updateAutoRestartSettings(@NonNull String serverId) {
        cancelAutoRestart(serverId);

        GameServer server = GameServer.getServerById(serverId);
        if (server == null) {
            log.warn("Auto restart setup failed: server {} not found.", serverId);
            return;
        }

        MapSettingsAdapter settings = new MapSettingsAdapter(server.getSettings().getGmcSettings());
        String cronExpression = settings.get("RestartCron");
        if (Utils.isNullOrEmpty(cronExpression)) {
            log.debug("No RestartCron configured for server '{}'. Skipping auto restart setup...", server.getFriendlyName());
            return;
        }

        ExecutionTime executionTime = parseCronExpression(cronExpression);
        if (executionTime == null) {
            log.warn("Invalid RestartCron '{}' for server '{}'. Skipping auto restart setup.", cronExpression, server.getFriendlyName());
            return;
        }

        scheduleNextRestart(server.getServerId(), server.getFriendlyName(), cronExpression, executionTime);
    }

    public static void cancelAutoRestart(@NonNull String serverId) {
        ScheduledFuture<?> future = RESTART_SCHEDULES.remove(serverId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private static void scheduleNextRestart(@NonNull String serverId, @NonNull String serverName, @NonNull String cronExpression, @NonNull ExecutionTime executionTime) {
        Optional<Duration> nextExecution = executionTime.timeToNextExecution(ZonedDateTime.now());
        if (nextExecution.isEmpty()) {
            log.warn("Could not compute next execution time for auto restart of server '{}' (cron: '{}').", serverName, cronExpression);
            return;
        }

        long delayMillis = Math.max(1000L, nextExecution.get().toMillis());

        Runnable task = () -> {

            try {
                GameServer server = GameServer.getServerById(serverId);
                if (server == null) {
                    log.warn("Skipping automatic restart: server {} no longer exists.", serverId);
                    return;
                }
                if (server.getState() != GameServerState.ONLINE) {
                    log.debug("Skipping automatic restart for server '{}' because it is not ONLINE (current state: {}).", server.getFriendlyName(), server.getState());
                    return;
                }
                log.info("Executing automatic restart for server '{}'.", server.getFriendlyName());
                server.restart().complete();
            } catch (Exception e) {
                log.error("Automatic restart for server {} failed.", serverId, e);
            } finally {
                scheduleNextRestart(serverId, serverName, cronExpression, executionTime);
            }
        };

        ScheduledFuture<?> future = Application.getExecutor().schedule(task, delayMillis, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> previous = RESTART_SCHEDULES.put(serverId, future);
        if (previous != null) {
            previous.cancel(false);
        }

        log.debug("Scheduled next automatic restart for server '{}' in {} seconds (cron: '{}').", serverName, TimeUnit.MILLISECONDS.toSeconds(delayMillis), cronExpression);
    }

    private static @Nullable ExecutionTime parseCronExpression(@NonNull String cronExpression) {
        String expr = cronExpression.trim();
        for (CronParser parser : List.of(QUARTZ_CRON, UNIX_CRON)) {
            try {
                Cron cron = parser.parse(expr);
                cron.validate();
                return ExecutionTime.forCron(cron);
            } catch (Exception ignored) {
                // Try next parser
            }
        }
        return null;
    }
}
