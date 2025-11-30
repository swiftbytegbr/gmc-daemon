package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class TimedMessageUtils {

    private TimedMessageUtils() {
    }

    public static List<Integer> getMessageMilestoneList(MapSettingsAdapter gmcSettings) {
        String csv = gmcSettings.get("DelayedMessageMilestones", null);
        if (CommonUtils.isNullOrEmpty(csv)) {
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

