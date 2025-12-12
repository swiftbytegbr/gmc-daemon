package de.swiftbyte.gmc.daemon.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectReader;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @BeforeEach
    void resetMapper() throws Exception {
        Field mapperField = Utils.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(null, null);
    }

    @Test
    void isNullOrEmptyHandlesNullAndEmpty() {
        assertTrue(Utils.isNullOrEmpty(null));
        assertTrue(Utils.isNullOrEmpty(""));
        assertFalse(Utils.isNullOrEmpty("value"));
        assertFalse(Utils.isNullOrEmpty(0));
    }

    @Test
    void valueOrDefaultPrefersNonNullValue() {
        assertEquals("value", Utils.valueOrDefault("value", "fallback"));
        assertEquals("fallback", Utils.valueOrDefault(null, "fallback"));
    }

    @Test
    void getObjectReaderAllowsNullPrimitives() throws Exception {
        ObjectReader reader = Utils.getObjectReader(PrimitiveHolder.class);

        PrimitiveHolder holder = reader.readValue("{\"number\":null,\"flag\":null}");

        assertEquals(0, holder.number);
        assertFalse(holder.flag);
    }

    private static class PrimitiveHolder {
        public int number;
        public boolean flag;
    }
}
