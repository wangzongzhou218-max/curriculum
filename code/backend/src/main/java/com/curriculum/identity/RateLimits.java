package com.curriculum.identity;

import com.curriculum.platform.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RateLimits {
    private final Store db;
    private final Crypto crypto;
    public RateLimits(Store db, Crypto crypto) { this.db = db; this.crypto = crypto; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Problem.class)
    public void take(String type, String key, int seconds, int limit) {
        long window = db.now().getEpochSecond() / seconds;
        String digest = crypto.digest("operation", "rate:" + key);
        db.update("INSERT INTO rate_bucket VALUES (?1,?2,?3,1) ON DUPLICATE KEY UPDATE request_count=request_count+1", type, digest, window);
        long count = ((Number) db.scalar("SELECT request_count FROM rate_bucket WHERE bucket_type=?1 AND key_digest=?2 AND window_start=?3", type, digest, window)).longValue();
        Problem.require(count <= limit, 429, "RATE_LIMITED", "操作过于频繁，请稍后重试");
    }
}
