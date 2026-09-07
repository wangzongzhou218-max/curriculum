package com.curriculum.course;

import com.curriculum.identity.Identity;
import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class Courses {
    public record Edit(String semesterId, String name, String description, List<Rules.Meeting> meetings) {}
    public record Publication(String semesterId, String target) {}
    private final Store db;
    private final Identity identity;
    public Courses(Store db, Identity identity) { this.db = db; this.identity = identity; }
    public Semester semester(long id) { Semester s = db.get(Semester.class, id); Problem.require(s != null, 400, "INVALID_SEMESTER", "学期不存在，请重新选择"); return s; }
    public Instant writable(long semesterId) {
        Semester semester = semester(semesterId); Instant decision = db.now();
        Problem.require(!Rules.closed(semester.endsOnExclusive, decision), 409, "SEMESTER_CLOSED", "本学期已结束，无法执行此操作"); return decision;
    }
    public Course own(Identity.Actor actor, long id, long semesterId) {
        identity.require(actor, "TEACHER"); Course course = db.get(Course.class, id);
        Problem.require(course != null && Objects.equals(course.teacherId, actor.userId()), 404, "RESOURCE_NOT_FOUND", "课程不存在或无权访问");
        Problem.require(course.semesterId == semesterId, 400, "INVALID_SEMESTER", "课程与所选学期不一致"); return course;
    }
    public Map<String, Object> create(Identity.Actor actor, Edit input) {
        identity.require(actor, "TEACHER"); long semesterId = Rules.id(input.semesterId());
        String name = Rules.text(input.name(), "课程名称", 100), description = Rules.text(input.description(), "课程描述", 2000);
        var meetings = Rules.meetings(input.meetings()); Instant decision = writable(semesterId);
        teacherScheduleAvailable(actor.userId(), semesterId, meetings);
        long id = ((Number) db.scalar("SELECT next_value FROM number_sequence WHERE name='COURSE'")).longValue();
        db.update("UPDATE number_sequence SET next_value=next_value+1 WHERE name='COURSE'");
        Course course = new Course(); course.id = id; course.code = "C%03d".formatted(id); course.paletteSlot = (int) ((id - 1) % 12);
        course.semesterId = semesterId; course.teacherId = actor.userId(); course.name = name; course.description = description;
        meetings.forEach(m -> course.meetings.add(new CourseMeeting(m))); course.createdAt = decision; course.updatedAt = decision; db.save(course);
        return result(course, true);
    }
    public Map<String, Object> edit(Identity.Actor actor, long id, Edit input, String version) {
        Course course = own(actor, id, Rules.id(input.semesterId())); available(course); Commands.version(course.version, version);
        String name = Rules.text(input.name(), "课程名称", 100), description = Rules.text(input.description(), "课程描述", 2000);
        var meetings = Rules.meetings(input.meetings());
        boolean changedTime = !meetings.equals(course.meetings.stream().map(CourseMeeting::view).toList());
        Problem.require(!changedTime || count(course.id) == 0, 409, "COURSE_HAS_STUDENTS", "已有学生选课，不能修改授课安排");
        Instant decision = writable(course.semesterId);
        db.track(course); course.name = name; course.description = description;
        if (changedTime) { course.meetings.clear(); meetings.forEach(m -> course.meetings.add(new CourseMeeting(m))); }
        course.version++; course.updatedAt = decision; return result(course, true);
    }
    public Map<String, Object> publication(Identity.Actor actor, long id, Publication input, String version) {
        Course course = own(actor, id, Rules.id(input.semesterId())); available(course); Commands.version(course.version, version);
        Problem.require(Set.of("PUBLISHED", "UNPUBLISHED").contains(Objects.toString(input.target(), "")), 400, "VALIDATION_FAILED", "课程状态无效");
        Problem.require(!("DRAFT".equals(course.publication) && "UNPUBLISHED".equals(input.target())), 409, "INVALID_COURSE_STATE", "尚未上架的课程不能下架");
        Instant decision = writable(course.semesterId); boolean changed = !course.publication.equals(input.target());
        if (changed) { db.track(course); course.publication = input.target(); course.updatedAt = decision; course.version++; }
        return result(course, changed);
    }
    public Map<String, Object> delete(Identity.Actor actor, long id, long semesterId, String version) {
        Course course = own(actor, id, semesterId); Commands.version(course.version, version);
        if (course.deletedAt != null) return result(course, false);
        Problem.require(count(id) == 0, 409, "COURSE_HAS_STUDENTS", "该课程仍有学生选课，无法删除");
        Instant decision = writable(semesterId); db.track(course); course.deletedAt = decision; course.updatedAt = decision; course.version++; return result(course, true);
    }
    public long count(long id) { return db.count("select count(e) from Enrollment e where e.courseId=?1 and e.state='ACTIVE'", id); }
    private void teacherScheduleAvailable(long teacherId, long semesterId, List<Rules.Meeting> meetings) {
        var courses = db.list(Course.class, "from Course where teacherId=?1 and semesterId=?2 and deletedAt is null", teacherId, semesterId);
        for (Course other : courses) {
            var otherMeetings = other.meetings.stream().map(CourseMeeting::view).toList();
            if (Rules.conflicts(meetings, otherMeetings))
                throw new Problem(409, "TEACHER_TIME_CONFLICT", "与 " + other.code + "「" + other.name + "」上课时间冲突，请修改课程时间后重试",
                    Map.of("courseCode", other.code, "courseName", other.name, "meetings", otherMeetings));
        }
    }
    public static void available(Course course) { Problem.require(course != null && course.deletedAt == null, 404, "RESOURCE_NOT_FOUND", "课程已删除或不存在"); }
    public static Map<String, Object> result(Course course, boolean changed) { return Map.of("entityId", course.id.toString(), "code", course.code, "version", Long.toString(course.version), "changed", changed); }
}
