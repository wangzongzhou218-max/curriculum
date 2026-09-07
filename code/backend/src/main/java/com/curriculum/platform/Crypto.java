package com.curriculum.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class Crypto {
    private final Map<String, String> keys;
    public Crypto(@Value("${app.session-key}") String session, @Value("${app.context-key}") String context,
                  @Value("${app.csrf-key}") String csrf, @Value("${app.operation-key}") String operation) {
        keys = Map.of("session", session, "context", context, "csrf", csrf, "operation", operation);
        if (keys.values().stream().anyMatch(v -> v.getBytes(StandardCharsets.UTF_8).length < 32) || new HashSet<>(keys.values()).size() != 4)
            throw new IllegalStateException("Four independent signing keys of at least 32 bytes are required");
    }
    public String digest(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keys.get(purpose).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public String sign(String purpose, String id) { return id + "." + digest(purpose, id); }
    public String verify(String purpose, String token) {
        if (token == null || token.length() > 180) return null;
        int separator = token.lastIndexOf('.');
        if (separator < 1) return null;
        String value = token.substring(0, separator);
        return equal(digest(purpose, value), token.substring(separator + 1)) ? value : null;
    }
    public static boolean equal(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
