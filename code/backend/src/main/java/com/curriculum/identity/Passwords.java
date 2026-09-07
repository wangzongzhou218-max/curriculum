package com.curriculum.identity;

import com.curriculum.platform.Problem;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
public class Passwords {
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    private final Semaphore slots = new Semaphore(4);
    private final String dummy = encoder.encode("not-a-real-account-password");
    public String encode(String value) { return limited(() -> encoder.encode(value)); }
    public boolean matches(String value, String hash) { return limited(() -> encoder.matches(value == null ? "" : value, hash == null ? dummy : hash)); }
    private <T> T limited(Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(200, TimeUnit.MILLISECONDS);
            if (!acquired) throw new Problem(429, "RATE_LIMITED", "认证请求较多，请稍后重试");
            return action.get();
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new Problem(503, "SERVICE_UNAVAILABLE", "服务暂不可用"); }
        finally { if (acquired) slots.release(); }
    }
}
