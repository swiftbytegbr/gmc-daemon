package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapSettingsAdapterTest {

    @Test
    void getReturnsStoredValuesAndDefaults() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("name", "value");
        settings.put("empty", "");
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        assertEquals("value", adapter.get("name"));
        assertEquals("fallback", adapter.get("missing", "fallback"));
        assertEquals("fallback", adapter.get("empty", "fallback"));
    }

    @Test
    void getIntAndBooleanApplyFallbacks() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("number", 5);
        settings.put("flag", true);
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        assertEquals(5, adapter.getInt("number"));
        assertEquals(7, adapter.getInt("missing", 7));
        assertTrue(adapter.getBoolean("flag"));
        assertFalse(adapter.getBoolean("missing", false));
    }

    @Test
    void hasAndNotEmptyDistinguishesEmptyValues() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("present", "data");
        settings.put("empty", "");
        MapSettingsAdapter adapter = new MapSettingsAdapter(settings);

        assertTrue(adapter.has("present"));
        assertTrue(adapter.hasAndNotEmpty("present"));
        assertTrue(adapter.has("empty"));
        assertFalse(adapter.hasAndNotEmpty("empty"));
        assertFalse(adapter.has("missing"));
    }
}
