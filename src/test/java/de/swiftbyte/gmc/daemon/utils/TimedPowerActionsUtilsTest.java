package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimedPowerActionsUtilsTest {

    @Test
    void getMessageMilestoneListParsesPositiveValues() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("DelayedMessageMilestones", "10, 20,30");
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        List<Integer> milestones = TimedPowerActionsUtils.getMessageMilestoneList(adapter);

        assertEquals(List.of(10, 20, 30), milestones);
    }

    @Test
    void getMessageMilestoneListFiltersInvalidEntries() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("DelayedMessageMilestones", "10,-5, 0");
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        List<Integer> milestones = TimedPowerActionsUtils.getMessageMilestoneList(adapter);

        assertEquals(List.of(10), milestones);
    }

    @Test
    void getMessageMilestoneListReturnsEmptyOnParseError() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("DelayedMessageMilestones", "abc,def");
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        List<Integer> milestones = TimedPowerActionsUtils.getMessageMilestoneList(adapter);

        assertTrue(milestones.isEmpty());
    }
}
