package com.curriculum.account;

import com.curriculum.identity.Identity;
import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class Accounts {
    public record Create(String role, String name, String registrationNumber, String email, String password, String confirmPassword) {}
    public record Edit(String name, String registrationNumber, String email) {}
    public record Reset(String password, String confirmPassword) {}
    private final Store db;
    private final Identity identity;
    private final SecureRandom random = new SecureRandom();
    public Accounts(Store db, Identity identity) { this.db = db; this.identity = identity; }
    public Map<String, Object> create(Identity.Actor actor, Create input, String hash, boolean registration) {
        if (registration) Problem.require(actor.userId() == null, 409, "VALIDATION_FAILED", "请先退出当前账号"); else identity.require(actor, "ADMIN");
        Problem.require(Set.of("TEACHER", "STUDENT").contains(Objects.toString(input.role(), "")), 400, "VALIDATION_FAILED", "请选择教师或学生");
        String name = Rules.text(input.name(), "姓名", 50), email = Rules.email(input.email());
        String number = registration ? null : Rules.registration(input.registrationNumber());
        if (!registration) unique(null, input.role(), number, email);
        String account = null;
        for (int i = 0; i < 10; i++) {
            String candidate = ("TEACHER".equals(input.role()) ? "t" : "s") + "%07d".formatted(random.nextInt(10_000_000));
            if (db.count("select count(a) from Account a where a.account=?1", candidate) == 0) { account = candidate; break; }
        }
        Problem.require(account != null, 503, "ACCOUNT_GENERATION_BUSY", "暂时无法创建账号，请稍后重试");
        if (registration) number = account;
        unique(null, input.role(), number, email);
        Account user = new Account(); user.account = account; user.role = input.role(); user.name = name;
        user.registrationNumber = number; user.emailNormalized = email; user.passwordHash = hash; user.createdAt = db.now(); user.updatedAt = user.createdAt;
        db.save(user); db.flush(); return view(user);
    }
    public Account target(Identity.Actor actor, long id) {
        identity.require(actor, "ADMIN");
        Account user = db.get(Account.class, id);
        Problem.require(user != null && !"ADMIN".equals(user.role), 404, "RESOURCE_NOT_FOUND", "账号不存在或不可管理"); return user;
    }
    public Map<String, Object> edit(Identity.Actor actor, long id, Edit input, String version) {
        Account user = target(actor, id); active(user); Commands.version(user.version, version);
        String name = Rules.text(input.name(), "姓名", 50), number = Rules.registration(input.registrationNumber()), email = Rules.email(input.email());
        unique(id, user.role, number, email);
        db.track(user); user.name = name; user.registrationNumber = number; user.emailNormalized = email; user.version++; user.updatedAt = db.now(); return view(user);
    }
    public Map<String, Object> reset(Identity.Actor actor, long id, String hash, String version) {
        Account user = target(actor, id); active(user); Commands.version(user.version, version);
        db.track(user); user.passwordHash = hash; user.authVersion++; user.version++; user.updatedAt = db.now(); revoke(user.id, user.updatedAt); return view(user);
    }
    public Map<String, Object> delete(Identity.Actor actor, long id, String version) {
        Account user = target(actor, id); Commands.version(user.version, version);
        if ("DELETED".equals(user.status)) return Map.of("changed", false, "cancelledCount", 0);
        List<Enrollment> selections = db.list(Enrollment.class, "from Enrollment where studentId=?1 and state='ACTIVE'", id);
        List<Semester> semesters = db.list(Semester.class, "from Semester");
        var open = new HashSet<Long>(); Instant now = db.now();
        semesters.stream().filter(s -> !Rules.closed(s.endsOnExclusive, now)).forEach(s -> open.add(s.id));
        if ("TEACHER".equals(user.role)) {
            var courses = db.list(Course.class, "from Course where teacherId=?1 and deletedAt is null", id);
            Problem.require(courses.stream().noneMatch(c -> open.contains(c.semesterId)), 409, "TEACHER_HAS_OPEN_COURSES", "该教师仍有未结束学期的课程，请先处理课程");
        }
        // One database time defines the boundary for every affected semester.
        Instant decision = db.now(); int cancelled = 0;
        for (Enrollment enrollment : selections) {
            Semester semester = semesters.stream().filter(s -> Objects.equals(s.id, enrollment.semesterId)).findFirst().orElseThrow();
            if (!Rules.closed(semester.endsOnExclusive, decision)) { db.track(enrollment); enrollment.state = "ACCOUNT_DELETED"; enrollment.endedAt = decision; enrollment.updatedAt = decision; enrollment.version++; cancelled++; }
        }
        db.track(user); user.status = "DELETED"; user.deletedAt = decision; user.updatedAt = decision; user.version++; user.authVersion++; revoke(id, decision);
        return Map.of("changed", true, "cancelledCount", cancelled, "entityId", Long.toString(id));
    }
    private void unique(Long id, String role, String number, String email) {
        var found = db.list(Account.class, "from Account where (role=?1 and registrationNumber=?2) or emailNormalized=?3", role, number, email);
        Problem.require(found.stream().allMatch(a -> Objects.equals(a.id, id)), 409, "ACCOUNT_FIELD_UNAVAILABLE", "工号、学号或邮箱已被使用");
    }
    private void active(Account account) { Problem.require("ACTIVE".equals(account.status), 404, "RESOURCE_NOT_FOUND", "账号已删除"); }
    private void revoke(long id, Instant now) { db.list(AuthSession.class, "from AuthSession where userId=?1 and revokedAt is null", id).forEach(s -> s.revokedAt = now); }
    public static Map<String, Object> view(Account a) {
        return Map.of("id", a.id.toString(), "account", a.account, "role", a.role, "name", a.name, "registrationNumber", Objects.toString(a.registrationNumber, ""),
            "email", Objects.toString(a.emailNormalized, ""), "version", Long.toString(a.version), "createdAt", a.createdAt.toString(), "status", a.status);
    }
}
