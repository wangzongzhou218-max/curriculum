package com.curriculum.identity;

import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class Identity {
    public record Actor(String contextId, String sessionId, Long userId) {}
    public record Credentials(Long userId, long authVersion, boolean valid) {}
    private final Store db;
    private final Passwords passwords;
    public Identity(Store db, Passwords passwords) { this.db = db; this.passwords = passwords; }
    @Transactional(readOnly = true)
    public Actor identify(String context, String session) {
        BrowserContext browser = context == null ? null : db.get(BrowserContext.class, context);
        Instant now = db.now();
        if (browser == null || !now.isBefore(browser.expiresAt)) return new Actor(null, null, null);
        AuthSession auth = session == null ? null : db.get(AuthSession.class, session);
        Account account = auth == null ? null : db.get(Account.class, auth.userId);
        if (!valid(auth, account, context, now)) return new Actor(context, null, null);
        return new Actor(context, session, account.id);
    }
    public Account require(Actor actor, String role) {
        Problem.require(actor != null && actor.userId() != null && actor.sessionId() != null, 401, "AUTH_REQUIRED", "请先登录");
        Account account = db.get(Account.class, actor.userId());
        AuthSession session = db.get(AuthSession.class, actor.sessionId());
        Problem.require(valid(session, account, actor.contextId(), db.now()), 401, "SESSION_EXPIRED", "会话已失效，请重新登录");
        Problem.require(role == null || role.equals(account.role), 403, "FORBIDDEN", "无权执行此操作");
        return account;
    }
    /** Only logout may authenticate an already revoked session, to replay its receipt. */
    @Transactional(readOnly = true)
    public Actor logoutActor(String context, String sessionId) {
        AuthSession session = sessionId == null ? null : db.get(AuthSession.class, sessionId);
        Problem.require(context != null && session != null && Objects.equals(context, session.contextId),
            401, "AUTH_REQUIRED", "请先登录");
        return new Actor(context, sessionId, session.userId);
    }
    private boolean valid(AuthSession session, Account account, String context, Instant now) {
        return session != null && account != null && "ACTIVE".equals(account.status) && session.revokedAt == null
            && session.authVersion == account.authVersion && Objects.equals(session.contextId, context)
            && now.isBefore(session.absoluteExpiresAt) && now.isBefore(session.lastInteractiveAt.plusSeconds(1800));
    }
    @Transactional(readOnly = true)
    public Credentials credentials(String account, String password) {
        var accounts = db.list(Account.class, "from Account where account=?1", account == null ? "" : account.strip());
        Account found = accounts.isEmpty() ? null : accounts.getFirst();
        boolean matched = passwords.matches(password, found == null ? null : found.passwordHash);
        return new Credentials(found == null ? null : found.id, found == null ? 0 : found.authVersion, matched && found != null && "ACTIVE".equals(found.status));
    }
    public Map<String, Object> view(Account account) {
        return Map.of("userId", account.id.toString(), "account", account.account, "name", account.name, "role", account.role,
            "homePath", switch (account.role) { case "ADMIN" -> "/admin/accounts"; case "TEACHER" -> "/teacher/courses"; default -> "/student/courses"; });
    }
}
