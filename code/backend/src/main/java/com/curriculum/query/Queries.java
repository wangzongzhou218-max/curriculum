package com.curriculum.query;

import com.curriculum.account.Accounts;
import com.curriculum.course.Courses;
import com.curriculum.identity.Identity;
import com.curriculum.platform.*;
import com.curriculum.platform.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class Queries {
    private final Store db;
    private final Identity identity;
    private final Courses courses;
    private final ObjectMapper json;
    public Queries(Store db, Identity identity, Courses courses, ObjectMapper json) { this.db = db; this.identity = identity; this.courses = courses; this.json = json; }
    public Map<String, Object> semesters(Identity.Actor actor) {
        identity.require(actor, null); Instant now = db.now();
        var semesters = db.list(Semester.class, "from Semester order by startsOn desc");
        Semester current = semesters.stream().filter(s -> !now.isBefore(s.startsOn.atStartOfDay(Rules.SCHOOL_ZONE).toInstant()) && !Rules.closed(s.endsOnExclusive, now)).findFirst().orElse(null);
        if (current == null) current = semesters.stream().filter(s -> now.isBefore(s.startsOn.atStartOfDay(Rules.SCHOOL_ZONE).toInstant())).min(Comparator.comparing(s -> s.startsOn)).orElse(semesters.getFirst());
        return Map.of("items", semesters.stream().map(s -> semesterView(s, now)).toList(), "defaultSemesterId", current.id.toString());
    }
    public Map<String, Object> accounts(Identity.Actor actor, String role, String query, int page, int size) {
        identity.require(actor, "ADMIN"); String q = search(query);
        Problem.require(role == null || role.isEmpty() || Set.of("TEACHER", "STUDENT").contains(role), 400, "VALIDATION_FAILED", "角色筛选无效");
        var list = db.list(Account.class, "from Account where role<>'ADMIN' and status='ACTIVE' order by createdAt desc, id desc").stream()
            .filter(a -> role == null || role.isEmpty() || a.role.equals(role))
            .filter(a -> a.name.contains(q) || a.account.startsWith(q) || a.registrationNumber.startsWith(q)).toList();
        return page(list, page, size, Accounts::view);
    }
    public Map<String, Object> account(Identity.Actor actor, long id) {
        identity.require(actor, "ADMIN"); Account account = db.get(Account.class, id);
        Problem.require(account != null && !"ADMIN".equals(account.role) && "ACTIVE".equals(account.status), 404, "RESOURCE_NOT_FOUND", "账号不存在");
        return Accounts.view(account);
    }
    public Map<String, Object> courseList(Identity.Actor actor, long semesterId, int page, int size, boolean teacher) {
        identity.require(actor, teacher ? "TEACHER" : "STUDENT"); Snapshot snapshot = snapshot(semesterId);
        var list = snapshot.courses.values().stream().filter(c -> teacher ? Objects.equals(c.teacherId, actor.userId()) : "PUBLISHED".equals(c.publication))
            .sorted(teacher ? Comparator.comparing((Course c) -> c.createdAt).reversed().thenComparing(c -> c.id, Comparator.reverseOrder()) : Comparator.comparing(c -> c.id)).toList();
        return page(list, page, size, c -> courseView(c, snapshot, actor, teacher));
    }
    public Map<String, Object> course(Identity.Actor actor, long id, long semesterId, boolean teacher) {
        identity.require(actor, teacher ? "TEACHER" : "STUDENT"); Snapshot snapshot = snapshot(semesterId); Course course = snapshot.courses.get(id);
        Problem.require(course != null && (teacher ? Objects.equals(course.teacherId, actor.userId()) : "PUBLISHED".equals(course.publication)), 404, "RESOURCE_NOT_FOUND", "课程不存在或不可访问");
        return courseView(course, snapshot, actor, teacher);
    }
    public Map<String, Object> enrollments(Identity.Actor actor, long semesterId) {
        identity.require(actor, "STUDENT"); Snapshot snapshot = snapshot(semesterId);
        var rows = ownEnrollments(actor, snapshot);
        return Map.of("items", rows.stream().map(e -> enrollmentView(e, snapshot, actor)).toList(), "totalSelected", rows.size(), "total", rows.size(), "page", 1, "size", 20);
    }
    public Map<String, Object> enrollment(Identity.Actor actor, long id, long semesterId) {
        identity.require(actor, "STUDENT"); Snapshot snapshot = snapshot(semesterId);
        Enrollment found = ownEnrollments(actor, snapshot).stream().filter(e -> e.id == id).findFirst().orElse(null);
        Problem.require(found != null, 404, "RESOURCE_NOT_FOUND", "有效选课不存在"); return enrollmentView(found, snapshot, actor);
    }
    public Map<String, Object> roster(Identity.Actor actor, long courseId, long semesterId, int page, int size) {
        identity.require(actor, "TEACHER"); Snapshot snapshot = snapshot(semesterId); Course course = snapshot.courses.get(courseId);
        Problem.require(course != null && Objects.equals(course.teacherId, actor.userId()), 404, "RESOURCE_NOT_FOUND", "课程不存在或无权查看名单");
        var rows = snapshot.enrollments.stream().filter(e -> e.courseId == courseId).sorted(Comparator.comparing(e -> snapshot.users.get(e.studentId).account)).toList();
        var result = new LinkedHashMap<>(page(rows, page, size, e -> {
            Account student = snapshot.users.get(e.studentId);
            return Map.of("studentId", student.id.toString(), "studentAccount", student.account, "studentName", student.name, "accountDeleted", "DELETED".equals(student.status), "enrolledAt", e.enrolledAt.toString());
        }));
        result.put("course", courseView(course, snapshot, actor, true)); result.put("selectedCount", rows.size()); return result;
    }
    public Map<String, Object> students(Identity.Actor actor, long semesterId, String query, String selection, String courseId, int page, int size) {
        identity.require(actor, "TEACHER"); String q = search(query).toLowerCase(Locale.ROOT);
        Problem.require(Set.of("ALL", "SELECTED", "NONE").contains(selection), 400, "VALIDATION_FAILED", "选课筛选无效");
        Snapshot snapshot = snapshot(semesterId);
        Long selectedCourseId = courseId == null || courseId.isBlank() ? null : Rules.id(courseId);
        if (selectedCourseId != null) {
            Course selectedCourse = snapshot.courses.get(selectedCourseId);
            Problem.require(selectedCourse != null && Objects.equals(selectedCourse.teacherId, actor.userId()), 400, "INVALID_COURSE_FILTER", "只能按自己的课程筛选学生");
        }
        var byStudent = snapshot.enrollments.stream().collect(Collectors.groupingBy(e -> e.studentId));
        var rows = snapshot.users.values().stream().filter(a -> "STUDENT".equals(a.role) && "ACTIVE".equals(a.status))
            .filter(a -> a.name.toLowerCase(Locale.ROOT).contains(q) || a.account.toLowerCase(Locale.ROOT).startsWith(q))
            .filter(a -> "ALL".equals(selection) || ("SELECTED".equals(selection) == byStudent.containsKey(a.id)))
            .filter(a -> selectedCourseId == null || byStudent.getOrDefault(a.id, List.of()).stream().anyMatch(e -> Objects.equals(e.courseId, selectedCourseId)))
            .sorted(Comparator.comparing(a -> a.account)).toList();
        return page(rows, page, size, student -> {
            var selected = byStudent.getOrDefault(student.id, List.of());
            return Map.of("studentId", student.id.toString(), "studentName", student.name, "studentAccount", student.account, "selectedCount", selected.size(),
                "courses", selected.stream().sorted(Comparator.comparing(e -> e.courseId)).map(e -> publicCourse(snapshot.courses.get(e.courseId), snapshot)).toList());
        });
    }
    public Map<String, Object> timetable(Identity.Actor actor, long semesterId, boolean teacher) {
        identity.require(actor, teacher ? "TEACHER" : "STUDENT"); Snapshot snapshot = snapshot(semesterId);
        Set<Long> selected = ownEnrollments(actor, snapshot).stream().map(e -> e.courseId).collect(Collectors.toSet());
        var list = snapshot.courses.values().stream().filter(c -> teacher ? Objects.equals(c.teacherId, actor.userId()) : selected.contains(c.id)).sorted(Comparator.comparing(c -> c.id)).toList();
        if (!teacher) for (int i = 0; i < list.size(); i++) for (int j = i + 1; j < list.size(); j++)
            Problem.require(!Rules.conflicts(meetings(list.get(i)), meetings(list.get(j))), 500, "DATA_INVARIANT_BROKEN", "课表数据出现时间冲突，请联系维护人员");
        return Map.of("semester", semesterView(snapshot.semester, snapshot.now), "courses", list.stream().map(c -> courseView(c, snapshot, actor, teacher)).toList(), "snapshotAt", snapshot.now.toString());
    }
    public Map<String, Object> operation(Identity.Actor actor, String key, boolean anonymous) {
        if (!anonymous) identity.require(actor, null);
        Problem.require(actor.contextId() != null, 401, "AUTH_REQUIRED", "原浏览器上下文已失效");
        String scope = anonymous ? "ANON:" + actor.contextId() : "USER:" + actor.userId();
        var list = db.list(Receipt.class, "from Receipt where scope=?1 and operationKey=?2", scope, key.toLowerCase(Locale.ROOT));
        Problem.require(!list.isEmpty(), 404, "OPERATION_NOT_FOUND", "暂未找到操作结果，请稍后再次查询"); Receipt receipt = list.getFirst();
        Problem.require(db.now().isBefore(receipt.expiresAt), 410, "OPERATION_EXPIRED", "操作记录已过期，请查看当前数据");
        Map<String, Object> body = json.readValue(receipt.resultData, Map.class);
        if (body.get("data") instanceof Map<?, ?> data) ((Map<?, ?>) data).remove("_session");
        return Map.of("outcome", receipt.outcome, "httpStatus", receipt.httpStatus, "result", body, "operationKey", key);
    }
    private Snapshot snapshot(long semesterId) {
        Semester semester = courses.semester(semesterId);
        var users = db.list(Account.class, "from Account").stream().collect(Collectors.toMap(a -> a.id, Function.identity()));
        var items = db.list(Course.class, "select distinct c from Course c left join fetch c.meetings where c.semesterId=?1 and c.deletedAt is null", semesterId).stream().collect(Collectors.toMap(c -> c.id, Function.identity()));
        var enrollments = db.list(Enrollment.class, "from Enrollment where semesterId=?1 and state='ACTIVE'", semesterId);
        return new Snapshot(semester, users, items, enrollments, db.now());
    }
    private record Snapshot(Semester semester, Map<Long, Account> users, Map<Long, Course> courses, List<Enrollment> enrollments, Instant now) {}
    private List<Enrollment> ownEnrollments(Identity.Actor actor, Snapshot snapshot) { return snapshot.enrollments.stream().filter(e -> actor.userId() != null && Objects.equals(e.studentId, actor.userId())).sorted(Comparator.comparing(e -> e.courseId)).toList(); }
    private Map<String, Object> enrollmentView(Enrollment e, Snapshot s, Identity.Actor actor) { return Map.of("id", e.id.toString(), "generation", Long.toString(e.generation), "version", Long.toString(e.version), "enrolledAt", e.enrolledAt.toString(), "semesterId", Long.toString(e.semesterId), "course", courseView(s.courses.get(e.courseId), s, actor, false)); }
    private Map<String, Object> publicCourse(Course c, Snapshot s) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", c.id.toString()); result.put("code", c.code); result.put("name", c.name); result.put("description", c.description);
        result.put("semester", semesterView(s.semester, s.now)); result.put("publication", c.publication); result.put("version", Long.toString(c.version));
        result.put("displayColor", Rules.COLORS[c.paletteSlot]); result.put("borderStyle", new String[]{"solid", "dashed", "double"}[(int) ((c.id - 1) / 12 % 3)]);
        result.put("teacher", Map.of("id", Long.toString(c.teacherId), "name", s.users.get(c.teacherId).name)); result.put("meetings", meetings(c)); return result;
    }
    private Map<String, Object> courseView(Course c, Snapshot s, Identity.Actor actor, boolean teacher) {
        Map<String, Object> result = publicCourse(c, s); long count = s.enrollments.stream().filter(e -> Objects.equals(e.courseId, c.id)).count();
        boolean selected = s.enrollments.stream().anyMatch(e -> Objects.equals(e.courseId, c.id) && actor.userId() != null && Objects.equals(e.studentId, actor.userId()));
        result.put("selected", selected); if (teacher) result.put("selectedCount", count);
        var actions = new ArrayList<String>();
        if (!Rules.closed(s.semester.endsOnExclusive, s.now)) {
            if (teacher) { actions.add("edit"); actions.add("PUBLISHED".equals(c.publication) ? "unpublish" : "publish"); if (count == 0) actions.add("delete"); }
            else if (selected) actions.addAll(List.of("drop", "swap")); else if ("PUBLISHED".equals(c.publication)) actions.add("select");
        }
        result.put("allowedActions", actions); return result;
    }
    public static Map<String, Object> semesterView(Semester s, Instant now) {
        String status = Rules.closed(s.endsOnExclusive, now) ? "ENDED" : now.isBefore(s.startsOn.atStartOfDay(Rules.SCHOOL_ZONE).toInstant()) ? "UPCOMING" : "ACTIVE";
        return Map.of("id", s.id.toString(), "academicYear", s.academicYear, "season", s.season, "startsOn", s.startsOn.toString(), "endsOnInclusive", s.endsOnExclusive.minusDays(1).toString(), "endsOnExclusive", s.endsOnExclusive.toString(), "status", status, "timezone", "Asia/Shanghai");
    }
    private List<Rules.Meeting> meetings(Course c) { return c.meetings.stream().map(CourseMeeting::view).toList(); }
    private String search(String query) { String q = query == null ? "" : query.strip(); Problem.require(q.codePointCount(0, q.length()) <= 100, 400, "VALIDATION_FAILED", "查询内容不能超过100个字符"); return q; }
    private <T> Map<String, Object> page(List<T> items, int page, int size, Function<T, ?> mapper) {
        Problem.require(page >= 1 && size >= 1 && size <= 100, 400, "VALIDATION_FAILED", "分页参数无效");
        int start = (int) Math.min((long) (page - 1) * size, items.size()), end = Math.min(start + size, items.size());
        return Map.of("items", items.subList(start, end).stream().map(mapper).toList(), "page", page, "size", size, "total", items.size());
    }
}
