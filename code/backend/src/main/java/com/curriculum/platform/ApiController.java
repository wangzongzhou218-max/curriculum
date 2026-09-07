package com.curriculum.platform;

import com.curriculum.account.Accounts;
import com.curriculum.course.Courses;
import com.curriculum.enrollment.Enrollments;
import com.curriculum.identity.*;
import com.curriculum.query.Queries;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    record Login(String account, String password) {}
    private final Commands commands;
    private final Identity identity;
    private final AuthService auth;
    private final Passwords passwords;
    private final RateLimits rates;
    private final Crypto crypto;
    private final Accounts accounts;
    private final Courses courses;
    private final Enrollments enrollments;
    private final Queries queries;
    private final Bootstrap bootstrap;
    private final boolean secure;
    public ApiController(Commands commands, Identity identity, AuthService auth, Passwords passwords, RateLimits rates, Crypto crypto,
                         Accounts accounts, Courses courses, Enrollments enrollments, Queries queries, Bootstrap bootstrap, @Value("${app.secure-cookie}") boolean secure) {
        this.commands = commands; this.identity = identity; this.auth = auth; this.passwords = passwords; this.rates = rates; this.crypto = crypto;
        this.accounts = accounts; this.courses = courses; this.enrollments = enrollments; this.queries = queries; this.bootstrap = bootstrap; this.secure = secure;
    }
    private Identity.Actor actor(HttpServletRequest request) { return (Identity.Actor) request.getAttribute("actor"); }
    private String version(HttpServletRequest request) { return request.getHeader("If-Match"); }
    private String requestId(HttpServletRequest request) { return Objects.toString(request.getAttribute("requestId"), UUID.randomUUID().toString()); }
    private Map<String, Object> read(HttpServletRequest request, Object value) { return Map.of("data", value, "meta", Map.of("requestId", requestId(request))); }
    private ResponseEntity<Map<String, Object>> write(HttpServletRequest request, Object input, int status, boolean anonymous, Supplier<Map<String, Object>> action) {
        var result = commands.execute(actor(request), request.getHeader("Idempotency-Key"), request.getMethod() + " " + request.getRequestURI(),
            Map.of("body", input, "query", new TreeMap<>(request.getParameterMap()), "generation", Objects.toString(request.getHeader("X-Enrollment-Generation"), "")), version(request), status, anonymous, action);
        return response(request, result);
    }
    private ResponseEntity<Map<String, Object>> response(HttpServletRequest request, Commands.Result result) {
        Map<String, Object> body = new LinkedHashMap<>(result.body());
        body.put("meta", Map.of("requestId", requestId(request), "operationKey", result.key(), "replayed", result.replayed(), "decidedAt", result.decidedAt().toString()));
        return ResponseEntity.status(result.status()).body(body);
    }
    @GetMapping("/auth/context") public Map<String, Object> context(HttpServletRequest request, HttpServletResponse response) {
        if (actor(request).contextId() == null) rates.take("CONTEXT", request.getRemoteAddr(), 60, 20);
        String id = commands.system(() -> auth.context(actor(request)));
        SecurityConfig.setCookie(response, "CURRICULUM_CONTEXT", crypto.sign("context", id), true, secure, 86400);
        String token = crypto.sign("csrf", id + ":" + UUID.randomUUID());
        SecurityConfig.setCookie(response, "CURRICULUM_CSRF", token, false, secure, 86400);
        return read(request, Map.of("ready", true));
    }
    @PostMapping("/auth/register") public ResponseEntity<?> register(HttpServletRequest request, @RequestBody Accounts.Create input) {
        rates.take("REGISTER", request.getRemoteAddr(), 3600, 10);
        String hash = passwords.encode(Rules.password(input.password(), input.confirmPassword()));
        return write(request, input, 201, true, () -> accounts.create(actor(request), input, hash, true));
    }
    @PostMapping("/auth/login") public ResponseEntity<?> login(HttpServletRequest request, HttpServletResponse response, @RequestBody Login input) {
        String account = input.account() == null ? "" : input.account().strip();
        rates.take("LOGIN_IP", request.getRemoteAddr(), 300, 100); rates.take("LOGIN_ACCOUNT", account, 300, 10);
        Problem.require(account.matches("admin|[ts][0-9]{7}") && input.password() != null && input.password().length() <= 256, 401, "AUTH_FAILED", "账号或密码错误");
        var credentials = identity.credentials(account, input.password());
        var result = commands.execute(actor(request), request.getHeader("Idempotency-Key"), request.getMethod() + " " + request.getRequestURI(), input, null, 200, true,
            () -> auth.login(actor(request), credentials));
        if (result.status() < 400) {
            Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) result.body().get("data"));
            String session = (String) data.remove("_session");
            Problem.require(auth.replayableSession(actor(request).contextId(), session), 401, "SESSION_EXPIRED", "原登录会话已失效，请重新登录");
            SecurityConfig.setCookie(response, "CURRICULUM_SESSION", crypto.sign("session", session), true, secure, 28800);
            SecurityConfig.setCookie(response, "CURRICULUM_CONTEXT", crypto.sign("context", actor(request).contextId()), true, secure, 86400);
            result = new Commands.Result(result.status(), Map.of("data", data), result.replayed(), result.key(), result.decidedAt());
        }
        return response(request, result);
    }
    @GetMapping("/auth/me") public Map<String, Object> me(HttpServletRequest request) { return read(request, auth.me(actor(request))); }
    @PostMapping("/auth/logout") public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        request.setAttribute("actor", identity.logoutActor(actor(request).contextId(),
            crypto.verify("session", SecurityConfig.cookie(request, "CURRICULUM_SESSION"))));
        var result = write(request, Map.of(), 200, false, () -> auth.logout(actor(request)));
        if (result.getStatusCode().is2xxSuccessful()) SecurityConfig.setCookie(response, "CURRICULUM_SESSION", "", true, secure, 0);
        return result;
    }
    @PostMapping("/auth/activity") public ResponseEntity<?> activity(HttpServletRequest request) { return write(request, Map.of(), 200, false, () -> auth.activity(actor(request))); }
    @GetMapping("/auth/operations/{key}") public Map<String, Object> anonymousOperation(HttpServletRequest request, @PathVariable String key) { return read(request, queries.operation(actor(request), key, true)); }
    @GetMapping("/operations/{key}") public Map<String, Object> operation(HttpServletRequest request, @PathVariable String key) { return read(request, queries.operation(actor(request), key, false)); }
    @GetMapping("/semesters") public Map<String, Object> semesters(HttpServletRequest request) {
        auth.me(actor(request)); commands.system(() -> { bootstrap.ensureYears(); return null; }); return read(request, queries.semesters(actor(request)));
    }
    @GetMapping("/admin/accounts") public Map<String, Object> accounts(HttpServletRequest request, @RequestParam(defaultValue = "") String role, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return read(request, queries.accounts(actor(request), role, q, page, size)); }
    @GetMapping("/admin/accounts/{id}") public Map<String, Object> account(HttpServletRequest request, @PathVariable long id) { return read(request, queries.account(actor(request), id)); }
    @PostMapping("/admin/accounts") public ResponseEntity<?> createAccount(HttpServletRequest request, @RequestBody Accounts.Create input) {
        auth.me(actor(request)); String hash = passwords.encode(Rules.password(input.password(), input.confirmPassword()));
        return write(request, input, 201, false, () -> accounts.create(actor(request), input, hash, false));
    }
    @PatchMapping("/admin/accounts/{id}") public ResponseEntity<?> editAccount(HttpServletRequest request, @PathVariable long id, @RequestBody Accounts.Edit input) { return write(request, input, 200, false, () -> accounts.edit(actor(request), id, input, version(request))); }
    @PostMapping("/admin/accounts/{id}/password-reset") public ResponseEntity<?> resetPassword(HttpServletRequest request, @PathVariable long id, @RequestBody Accounts.Reset input) {
        auth.me(actor(request)); String hash = passwords.encode(Rules.password(input.password(), input.confirmPassword()));
        return write(request, input, 200, false, () -> accounts.reset(actor(request), id, hash, version(request)));
    }
    @DeleteMapping("/admin/accounts/{id}") public ResponseEntity<?> deleteAccount(HttpServletRequest request, @PathVariable long id) { return write(request, Map.of(), 200, false, () -> accounts.delete(actor(request), id, version(request))); }
    @GetMapping("/teacher/courses") public Map<String, Object> teacherCourses(HttpServletRequest request, @RequestParam long semesterId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return read(request, queries.courseList(actor(request), semesterId, page, size, true)); }
    @GetMapping("/teacher/courses/{id}") public Map<String, Object> teacherCourse(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId) { return read(request, queries.course(actor(request), id, semesterId, true)); }
    @PostMapping("/teacher/courses") public ResponseEntity<?> createCourse(HttpServletRequest request, @RequestBody Courses.Edit input) { return write(request, input, 201, false, () -> courses.create(actor(request), input)); }
    @PutMapping("/teacher/courses/{id}") public ResponseEntity<?> editCourse(HttpServletRequest request, @PathVariable long id, @RequestBody Courses.Edit input) { return write(request, input, 200, false, () -> courses.edit(actor(request), id, input, version(request))); }
    @PostMapping("/teacher/courses/{id}/publication") public ResponseEntity<?> publication(HttpServletRequest request, @PathVariable long id, @RequestBody Courses.Publication input) { return write(request, input, 200, false, () -> courses.publication(actor(request), id, input, version(request))); }
    @DeleteMapping("/teacher/courses/{id}") public ResponseEntity<?> deleteCourse(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId) { return write(request, Map.of(), 200, false, () -> courses.delete(actor(request), id, semesterId, version(request))); }
    @GetMapping("/teacher/courses/{id}/students") public Map<String, Object> roster(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return read(request, queries.roster(actor(request), id, semesterId, page, size)); }
    @GetMapping("/teacher/students") public Map<String, Object> students(HttpServletRequest request, @RequestParam long semesterId, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "ALL") String selection, @RequestParam(defaultValue = "") String courseId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return read(request, queries.students(actor(request), semesterId, q, selection, courseId, page, size)); }
    @GetMapping("/teacher/timetable") public Map<String, Object> teacherTimetable(HttpServletRequest request, @RequestParam long semesterId) { return read(request, queries.timetable(actor(request), semesterId, true)); }
    @GetMapping("/student/catalog") public Map<String, Object> catalog(HttpServletRequest request, @RequestParam long semesterId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return read(request, queries.courseList(actor(request), semesterId, page, size, false)); }
    @GetMapping("/student/catalog/{id}") public Map<String, Object> catalogCourse(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId) { return read(request, queries.course(actor(request), id, semesterId, false)); }
    @GetMapping("/student/enrollments") public Map<String, Object> selections(HttpServletRequest request, @RequestParam long semesterId) { return read(request, queries.enrollments(actor(request), semesterId)); }
    @GetMapping("/student/enrollments/{id}") public Map<String, Object> selection(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId) { return read(request, queries.enrollment(actor(request), id, semesterId)); }
    @PostMapping("/student/enrollments") public ResponseEntity<?> select(HttpServletRequest request, @RequestBody Enrollments.Select input) { return write(request, input, 200, false, () -> enrollments.select(actor(request), input)); }
    @DeleteMapping("/student/enrollments/{id}") public ResponseEntity<?> drop(HttpServletRequest request, @PathVariable long id, @RequestParam long semesterId) { return write(request, Map.of(), 200, false, () -> enrollments.drop(actor(request), id, semesterId, version(request), request.getHeader("X-Enrollment-Generation"))); }
    @PostMapping("/student/enrollments/{id}/swap") public ResponseEntity<?> swap(HttpServletRequest request, @PathVariable long id, @RequestBody Enrollments.Swap input) { return write(request, input, 200, false, () -> enrollments.swap(actor(request), id, input, version(request))); }
    @GetMapping("/student/timetable") public Map<String, Object> studentTimetable(HttpServletRequest request, @RequestParam long semesterId) { return read(request, queries.timetable(actor(request), semesterId, false)); }
}
