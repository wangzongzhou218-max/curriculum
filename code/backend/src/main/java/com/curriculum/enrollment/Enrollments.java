package com.curriculum.enrollment;

import com.curriculum.course.Courses;
import com.curriculum.identity.Identity;
import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class Enrollments {
    public record Select(String semesterId, String courseId) {}
    public record Swap(String semesterId, String targetCourseId, String sourceGeneration) {}
    private final Store db;
    private final Identity identity;
    private final Courses courses;
    public Enrollments(Store db, Identity identity, Courses courses) { this.db = db; this.identity = identity; this.courses = courses; }
    public Map<String, Object> select(Identity.Actor actor, Select input) {
        identity.require(actor, "STUDENT"); long semesterId = Rules.id(input.semesterId()), courseId = Rules.id(input.courseId());
        Course target = target(courseId, semesterId, false);
        var existing = relation(actor.userId(), courseId);
        if (existing != null && "ACTIVE".equals(existing.state)) return result(existing, false);
        selectable(target);
        var active = active(actor.userId(), semesterId);
        Problem.require(active.size() < 4, 409, "MAX_COURSES_REACHED", "本学期最多选择四门课程");
        conflicts(target, active); Instant decision = courses.writable(semesterId);
        return result(activate(existing, actor.userId(), target, decision), true);
    }
    public Map<String, Object> drop(Identity.Actor actor, long id, long semesterId, String version, String generation) {
        Enrollment source = source(actor, id, semesterId, version, generation);
        Instant decision = courses.writable(semesterId); end(source, "DROPPED", decision); return result(source, true);
    }
    public Map<String, Object> swap(Identity.Actor actor, long id, Swap input, String version) {
        long semesterId = Rules.id(input.semesterId()); Enrollment source = source(actor, id, semesterId, version, input.sourceGeneration());
        long courseId = Rules.id(input.targetCourseId());
        if (source.courseId == courseId) { courses.writable(semesterId); return result(source, false); }
        Course target = target(courseId, semesterId, true); selectable(target);
        Enrollment existing = relation(actor.userId(), courseId);
        Problem.require(existing == null || !"ACTIVE".equals(existing.state), 409, "TARGET_ALREADY_SELECTED", "目标课程已在你的已选课程中");
        var remaining = active(actor.userId(), semesterId).stream().filter(e -> !e.id.equals(source.id)).toList();
        conflicts(target, remaining); Instant decision = courses.writable(semesterId);
        end(source, "SWAPPED_OUT", decision); Enrollment selected = activate(existing, actor.userId(), target, decision);
        return Map.of("sourceId", source.id.toString(), "targetId", selected.id.toString(), "changed", true);
    }
    private Enrollment source(Identity.Actor actor, long id, long semesterId, String version, String generation) {
        identity.require(actor, "STUDENT"); Enrollment source = db.get(Enrollment.class, id);
        Problem.require(source != null && Objects.equals(source.studentId, actor.userId()), 404, "RESOURCE_NOT_FOUND", "选课记录不存在");
        Problem.require(source.semesterId == semesterId, 400, "INVALID_SEMESTER", "选课与所选学期不一致");
        Problem.require(version != null && generation != null, 428, "PRECONDITION_REQUIRED", "请先刷新已选课程");
        Problem.require("ACTIVE".equals(source.state) && Long.toString(source.generation).equals(generation) && ("\"" + source.version + "\"").equals(version), 409, "SOURCE_CHANGED", "来源选课已变化，请重新选择");
        return source;
    }
    private Course target(long id, long semesterId, boolean swap) {
        courses.semester(semesterId); Course target = db.get(Course.class, id); Courses.available(target);
        Problem.require(target.semesterId == semesterId, swap ? 409 : 400, swap ? "CROSS_SEMESTER_SWAP" : "INVALID_SEMESTER", "不能跨学期选择或换课"); return target;
    }
    private void selectable(Course target) { Problem.require("PUBLISHED".equals(target.publication), 409, "COURSE_UNAVAILABLE", "课程当前未上架，无法选入"); }
    private List<Enrollment> active(long student, long semester) { return db.list(Enrollment.class, "from Enrollment where studentId=?1 and semesterId=?2 and state='ACTIVE'", student, semester); }
    private Enrollment relation(long student, long course) { var list = db.list(Enrollment.class, "from Enrollment where studentId=?1 and courseId=?2", student, course); return list.isEmpty() ? null : list.getFirst(); }
    private void conflicts(Course target, List<Enrollment> active) {
        var targetTimes = target.meetings.stream().map(CourseMeeting::view).toList();
        for (Enrollment enrollment : active) {
            Course course = db.get(Course.class, enrollment.courseId);
            if (Rules.conflicts(targetTimes, course.meetings.stream().map(CourseMeeting::view).toList()))
                throw new Problem(409, "TIME_CONFLICT", "与 " + course.code + "「" + course.name + "」上课时间冲突", Map.of("courseCode", course.code, "courseName", course.name, "meetings", course.meetings.stream().map(CourseMeeting::view).toList()));
        }
    }
    private Enrollment activate(Enrollment existing, long student, Course course, Instant time) {
        boolean fresh = existing == null; Enrollment result = fresh ? new Enrollment() : existing;
        if (!fresh) { db.track(result); result.generation++; result.version++; }
        result.studentId = student; result.courseId = course.id; result.semesterId = course.semesterId;
        result.state = "ACTIVE"; result.enrolledAt = time; result.updatedAt = time; result.endedAt = null;
        if (fresh) db.save(result); db.flush(); return result;
    }
    private void end(Enrollment source, String state, Instant time) { db.track(source); source.state = state; source.endedAt = time; source.updatedAt = time; source.version++; }
    private Map<String, Object> result(Enrollment e, boolean changed) { return Map.of("entityId", e.id.toString(), "generation", Long.toString(e.generation), "version", Long.toString(e.version), "changed", changed); }
}
