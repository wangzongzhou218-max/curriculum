package com.curriculum.identity;

import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AuthService {
    private final Store db;
    private final Identity identity;
    public AuthService(Store db, Identity identity) { this.db = db; this.identity = identity; }
    public String context(Identity.Actor actor) {
        if (actor.contextId() != null) return actor.contextId();
        BrowserContext context = new BrowserContext(); context.id = UUID.randomUUID().toString(); context.createdAt = db.now(); context.expiresAt = context.createdAt.plusSeconds(86400); db.save(context);
        return context.id;
    }
    public Map<String, Object> login(Identity.Actor actor, Identity.Credentials credentials) {
        Problem.require(actor.userId() == null, 409, "VALIDATION_FAILED", "请先退出当前账号");
        Account account = credentials.userId() == null ? null : db.get(Account.class, credentials.userId());
        Problem.require(credentials.valid() && account != null && account.authVersion == credentials.authVersion() && "ACTIVE".equals(account.status), 401, "AUTH_FAILED", "账号或密码错误");
        AuthSession session = new AuthSession(); session.id = UUID.randomUUID().toString(); session.contextId = actor.contextId(); session.userId = account.id;
        session.authVersion = account.authVersion; session.issuedAt = db.now(); session.lastInteractiveAt = session.issuedAt; session.absoluteExpiresAt = session.issuedAt.plusSeconds(28800);
        db.get(BrowserContext.class, actor.contextId()).expiresAt = session.issuedAt.plusSeconds(86400); db.save(session);
        Map<String, Object> result = new LinkedHashMap<>(identity.view(account)); result.put("_session", session.id); return result;
    }
    public Map<String, Object> logout(Identity.Actor actor) {
        var session = db.get(AuthSession.class, actor.sessionId());
        if (session.revokedAt != null) return Map.of("changed", false);
        session.revokedAt = db.now();
        return Map.of("changed", true);
    }
    public Map<String, Object> activity(Identity.Actor actor) {
        identity.require(actor, null);
        var session = db.get(AuthSession.class, actor.sessionId());
        if (db.now().isAfter(session.lastInteractiveAt.plusSeconds(60))) session.lastInteractiveAt = db.now();
        return Map.of("changed", true);
    }
    @Transactional(readOnly = true) public Map<String, Object> me(Identity.Actor actor) { return identity.view(identity.require(actor, null)); }
    @Transactional(readOnly = true) public boolean replayableSession(String contextId, String sessionId) { return identity.identify(contextId, sessionId).userId() != null; }
}
