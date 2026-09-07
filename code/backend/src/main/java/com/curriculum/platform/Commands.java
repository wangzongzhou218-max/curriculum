package com.curriculum.platform;

import com.curriculum.identity.Identity;
import com.curriculum.identity.Identity.Actor;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class Commands {
    public record Result(int status, Map<String, Object> body, boolean replayed, String key, Instant decidedAt) {}
    private final Store db;
    private final Identity identity;
    private final Crypto crypto;
    private final ObjectMapper json;
    private final TransactionTemplate write;
    public Commands(Store db, Identity identity, Crypto crypto, ObjectMapper json, PlatformTransactionManager manager) {
        this.db = db; this.identity = identity; this.crypto = crypto; this.json = json;
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(5);
    }
    public <T> T system(Supplier<T> action) {
        return write.execute(status -> {
            db.beginAudit();
            try { db.update("SET SESSION innodb_lock_wait_timeout=1"); db.lock(); return action.get(); }
            finally { db.endAudit(); }
        });
    }
    public Result execute(Actor actor, String key, String route, Object input, String version, int status, boolean anonymous, Supplier<Map<String, Object>> action) {
        Problem.require(key != null && key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"), 400, "INVALID_OPERATION_KEY", "操作标识无效，请刷新后重试");
        Problem.require(actor.contextId() != null, 403, "CSRF_INVALID", "请刷新页面后重试");
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String digest = crypto.digest("operation", route + "\n" + Objects.toString(version, "") + "\n" + json.writeValueAsString(canonical(input)));
        return system(() -> {
            if (!anonymous) {
                if (route.equals("POST /api/v1/auth/logout")) identity.logoutActor(actor.contextId(), actor.sessionId());
                else identity.require(actor, null);
            }
            String scope = anonymous ? "ANON:" + actor.contextId() : "USER:" + actor.userId();
            var matches = db.list(Receipt.class, "from Receipt where scope=?1 and operationKey=?2", scope, normalizedKey);
            if (!matches.isEmpty()) {
                Receipt receipt = matches.getFirst();
                Problem.require(db.now().isBefore(receipt.expiresAt), 410, "OPERATION_EXPIRED", "操作记录已过期，请查看当前数据");
                Problem.require(Crypto.equal(digest, receipt.requestDigest), 409, "OPERATION_KEY_REUSED", "同一操作标识不能用于不同内容");
                return new Result(receipt.httpStatus, json.readValue(receipt.resultData, Map.class), true, normalizedKey, receipt.createdAt);
            }
            Map<String, Object> body;
            int responseStatus = status;
            try { body = Map.of("data", action.get()); }
            catch (Problem problem) {
                if (db.hasAuditedChanges()) throw new IllegalStateException("Business rejection after domain mutation", problem);
                responseStatus = problem.status;
                body = Map.of("error", Map.of("code", problem.code, "message", problem.getMessage(), "details", problem.details));
            }
            String serialized = json.writeValueAsString(body);
            if (serialized.getBytes(StandardCharsets.UTF_8).length > 16384) throw new IllegalStateException("Operation receipt exceeds size limit");
            Instant time = db.now();
            Receipt receipt = new Receipt(); receipt.scope = scope; receipt.operationKey = normalizedKey; receipt.requestDigest = digest;
            receipt.httpStatus = responseStatus; receipt.outcome = responseStatus < 400 ? "SUCCEEDED" : "REJECTED";
            receipt.resultData = serialized; receipt.createdAt = time; receipt.expiresAt = time.plusSeconds(86400);
            db.save(receipt); db.flush();
            DomainEvent event = new DomainEvent(); event.actorId = actor.userId(); event.operationId = receipt.id;
            event.eventType = receipt.outcome; event.target = route; event.occurredAt = time; db.save(event);
            if (responseStatus < 400) for (var change : db.auditChanges()) {
                DomainEvent detail = new DomainEvent(); detail.actorId = actor.userId(); detail.operationId = receipt.id;
                detail.eventType = "CHANGED"; detail.target = route; detail.occurredAt = time;
                detail.entityType = change.type(); detail.entityId = change.id(); detail.semesterId = change.semesterId();
                detail.beforeData = json.writeValueAsString(change.before()); detail.afterData = json.writeValueAsString(change.after()); db.save(detail);
            }
            db.update("UPDATE command_guard SET revision=revision+1 WHERE id=1"); db.flush();
            return new Result(responseStatus, body, false, normalizedKey, time);
        });
    }
    private Object canonical(Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> result = new TreeMap<>(); map.forEach((k, v) -> result.put(k.toString(), canonical(v))); return result;
        }
        if (value instanceof Collection<?> list) return list.stream().map(this::canonical).toList();
        if (value instanceof Object[] array) return Arrays.stream(array).map(this::canonical).toList();
        if (value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        return canonical(json.convertValue(value, Map.class));
    }
    public static void version(long current, String supplied) {
        Problem.require(supplied != null, 428, "PRECONDITION_REQUIRED", "请先读取最新数据");
        Problem.require(("\"" + current + "\"").equals(supplied), 409, "VERSION_CONFLICT", "数据已更新，请刷新后重新确认");
    }
}
