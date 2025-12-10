package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class TimedPowerActionsUtils {

    private TimedPowerActionsUtils() {
    }

    /**
     * Sleeps for one minute unless interrupted, restoring interrupt status when interrupted.
     */
    public static void sleepInterruptibly() {
        long millis = TimeUnit.MINUTES.toMillis(1);
        long end = System.currentTimeMillis() + millis;
        while (true) {
            long remaining = end - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                //noinspection BusyWait
                Thread.sleep(Math.min(remaining, 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Parses a comma-separated list of integers from settings and filters out invalid values.
     *
     * @param gmcSettings settings adapter containing the {@code DelayedMessageMilestones} entry
     * @return list of positive integer milestones; empty when missing or invalid
     */
    public static @NonNull List<@NonNull Integer> getMessageMilestoneList(@NonNull MapSettingsAdapter gmcSettings) {
        String csv = gmcSettings.get("DelayedMessageMilestones");
        if (Utils.isNullOrEmpty(csv)) {
            return List.of();
        }

        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .mapToInt(Integer::parseInt)
                    .filter(v -> v > 0)
                    .boxed()
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
