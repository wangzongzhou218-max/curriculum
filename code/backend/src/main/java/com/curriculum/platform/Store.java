package com.curriculum.platform;

import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import com.curriculum.platform.Models.*;

@Repository
public class Store {
    @PersistenceContext private EntityManager em;
    private final ThreadLocal<IdentityHashMap<Object, Map<String, Object>>> audit = new ThreadLocal<>();
    public <T> T get(Class<T> type, Object id) { return em.find(type, id); }
    public <T> T save(T entity) {
        if (audit.get() != null && isDomain(entity)) audit.get().put(entity, Map.of());
        em.persist(entity); return entity;
    }
    public void beginAudit() { audit.set(new IdentityHashMap<>()); }
    public void endAudit() { audit.remove(); }
    public void track(Object entity) { if (audit.get() != null && isDomain(entity)) audit.get().putIfAbsent(entity, snapshot(entity)); }
    private boolean isDomain(Object entity) { return entity instanceof Account || entity instanceof Course || entity instanceof Enrollment; }
    public record Change(String type, Long id, Long semesterId, Map<String, Object> before, Map<String, Object> after) {}
    public List<Change> auditChanges() {
        if (audit.get() == null) return List.of();
        List<Change> changes = new ArrayList<>();
        audit.get().forEach((entity, before) -> {
            var after = snapshot(entity);
            if (!before.equals(after)) {
                String type = entity instanceof Account ? "USER" : entity instanceof Course ? "COURSE" : "ENROLLMENT";
                Long id = entity instanceof Account a ? a.id : entity instanceof Course c ? c.id : ((Enrollment) entity).id;
                Long semester = entity instanceof Course c ? c.semesterId : entity instanceof Enrollment e ? e.semesterId : null;
                changes.add(new Change(type, id, semester, before, after));
            }
        }); return changes;
    }
    public boolean hasAuditedChanges() { return !auditChanges().isEmpty(); }
    private Map<String, Object> snapshot(Object entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity instanceof Account a) {
            result.put("account", a.account); result.put("name", a.name); result.put("role", a.role); result.put("status", a.status);
            result.put("version", a.version); result.put("authVersion", a.authVersion); result.put("registrationNumber", a.registrationNumber); result.put("email", a.emailNormalized);
        } else if (entity instanceof Course c) {
            result.put("code", c.code); result.put("name", c.name); result.put("description", c.description); result.put("teacherId", c.teacherId);
            result.put("publication", c.publication); result.put("version", c.version); result.put("deletedAt", Objects.toString(c.deletedAt, null));
            result.put("meetings", c.meetings.stream().map(CourseMeeting::view).toList());
        } else if (entity instanceof Enrollment e) {
            result.put("studentId", e.studentId); result.put("courseId", e.courseId); result.put("state", e.state); result.put("generation", e.generation); result.put("version", e.version);
            result.put("enrolledAt", Objects.toString(e.enrolledAt, null)); result.put("endedAt", Objects.toString(e.endedAt, null));
        }
        return result;
    }
    public void flush() { em.flush(); }
    public void clear() { em.clear(); }
    public <T> List<T> list(Class<T> type, String jpql, Object... params) {
        var query = em.createQuery(jpql, type);
        for (int i = 0; i < params.length; i++) query.setParameter(i + 1, params[i]);
        return query.getResultList();
    }
    public long count(String jpql, Object... params) { return list(Long.class, jpql, params).getFirst(); }
    public Object scalar(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) query.setParameter(i + 1, params[i]);
        return query.getSingleResult();
    }
    public int update(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) query.setParameter(i + 1, params[i]);
        return query.executeUpdate();
    }
    public Instant now() {
        Object value = scalar("SELECT UTC_TIMESTAMP(6)");
        if (value instanceof Instant instant) return instant;
        var time = value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (java.time.LocalDateTime) value;
        return time.toInstant(java.time.ZoneOffset.UTC);
    }
    public void lock() { scalar("SELECT revision FROM command_guard WHERE id=1 FOR UPDATE"); }
}
