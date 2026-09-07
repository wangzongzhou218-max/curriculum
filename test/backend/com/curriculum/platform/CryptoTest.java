package com.curriculum.platform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoTest {
    @Test void rejectsTamperingAndCrossPurposeTokens() {
        var crypto = new Crypto("a".repeat(32), "b".repeat(32), "c".repeat(32), "d".repeat(32));
        var token = crypto.sign("session", "session-id");
        assertEquals("session-id", crypto.verify("session", token));
        assertNull(crypto.verify("context", token));
        assertNull(crypto.verify("session", token + "x"));
        assertNull(crypto.verify("session", null));
        assertThrows(IllegalStateException.class, () -> new Crypto("a".repeat(32), "a".repeat(32), "c".repeat(32), "d".repeat(32)));
    }
}
