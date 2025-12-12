package de.swiftbyte.gmc.daemon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeUtilsTest {

    @Test
    void getValidatedTokenAcceptsSixDigitTokens() {
        Integer token = NodeUtils.getValidatedToken("123-456");

        assertNotNull(token);
        assertEquals(123456, token);
    }

    @Test
    void getValidatedTokenRejectsIncorrectLength() {
        assertNull(NodeUtils.getValidatedToken("12345"));
        assertNull(NodeUtils.getValidatedToken("1234567"));
    }

    @Test
    void getValidatedTokenRejectsNonNumericTokens() {
        assertNull(NodeUtils.getValidatedToken("abc-def"));
    }
}
