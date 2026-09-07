package com.curriculum;

import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL; unavailable Docker is a failure, never an implicit skip or H2 fallback. */
class CurriculumIT {
    static MySQLContainer mysql;
    static ConfigurableApplicationContext app;
    static String base;
    static final ObjectMapper json = new ObjectMapper();
    static final String PASSWORD = "Testing-123";

    @BeforeAll static void start() {
        mysql = new MySQLContainer("mysql:8.4.11");
        mysql.start();
        var application = new SpringApplication(Application.class);
        application.setDefaultProperties(Map.ofEntries(
            Map.entry("server.port", "0"),
            Map.entry("DB_URL", mysql.getJdbcUrl()), Map.entry("DB_USERNAME", mysql.getUsername()), Map.entry("DB_PASSWORD", mysql.getPassword()),
            Map.entry("FLYWAY_USER", mysql.getUsername()), Map.entry("FLYWAY_PASSWORD", mysql.getPassword()),
            Map.entry("SESSION_SIGNING_KEY", "s".repeat(32)), Map.entry("CONTEXT_SIGNING_KEY", "c".repeat(32)),
            Map.entry("CSRF_SIGNING_KEY", "f".repeat(32)), Map.entry("OPERATION_HMAC_KEY", "o".repeat(32))));
        app = application.run("--server.port=0");
        base = "http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port") + "/api/v1";
    }
    @AfterAll static void stop() { if (app != null) app.close(); if (mysql != null) mysql.stop(); }

    static class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        JsonNode call(String method, String path, Object body, int expected, String key, String version) throws Exception {
            var request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json").header("Origin", "http://localhost:3000");
            if (!method.equals("GET")) {
                String csrf = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("CURRICULUM_CSRF")).findFirst().orElseThrow().getValue();
                request.header("X-CSRF-Token", csrf).header("Idempotency-Key", key == null ? UUID.randomUUID().toString() : key);
                if (version != null) request.header("If-Match", "\"" + version + "\"");
            }
            request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(expected, response.statusCode(), method + " " + path + ": " + response.body());
            return json.readTree(response.body());
        }
        JsonNode get(String path) throws Exception { return call("GET", path, null, 200, null, null).get("data"); }
        JsonNode post(String path, Object body, int status) throws Exception { return call("POST", path, body, status, null, null).get("data"); }
        void login(String account, String password) throws Exception { get("/auth/context"); post("/auth/login", Map.of("account", account, "password", password), 200); }
    }
    static Browser register(String role, String number) throws Exception {
        var browser = new Browser(); browser.get("/auth/context");
        var created = browser.post("/auth/register", Map.of("role", role, "name", "测试" + role,
            "email", number + "@example.edu", "password", PASSWORD, "confirmPassword", PASSWORD), 201);
        assertTrue(created.get("account").asText().matches(role.equals("TEACHER") ? "t[0-9]{7}" : "s[0-9]{7}"));
        browser.login(created.get("account").asText(), PASSWORD); return browser;
    }

    @Test void registrationRoleBoundaryPublicationAndSelectionReplay() throws Exception {
        var teacher = register("TEACHER", "T001"); var student = register("STUDENT", "S001");
        String semester = teacher.get("/semesters").get("defaultSemesterId").asText();
        student.call("GET", "/teacher/courses?semesterId=" + semester, null, 403, null, null);
        var draft = teacher.post("/teacher/courses", Map.of("semesterId", semester, "name", "离散数学", "description", "基础课程",
            "meetings", List.of(Map.of("weekday", 1, "startTime", "08:00", "endTime", "10:00"))), 201);
        String id = draft.get("entityId").asText();
        var conflict = teacher.call("POST", "/teacher/courses", Map.of("semesterId", semester, "name", "冲突课程", "description", "冲突安排",
            "meetings", List.of(Map.of("weekday", 1, "startTime", "09:00", "endTime", "11:00"))), 409, null, null);
        assertEquals("TEACHER_TIME_CONFLICT", conflict.at("/error/code").asText());
        assertEquals(0, student.get("/student/catalog?semesterId=" + semester).get("total").asInt());
        teacher.call("POST", "/teacher/courses/" + id + "/publication", Map.of("semesterId", semester, "target", "PUBLISHED"), 200, null, draft.get("version").asText());
        assertEquals(1, student.get("/student/catalog?semesterId=" + semester).get("total").asInt());
        String key = UUID.randomUUID().toString(); var input = Map.of("semesterId", semester, "courseId", id);
        var first = student.call("POST", "/student/enrollments", input, 200, key, null);
        var replay = student.call("POST", "/student/enrollments", input, 200, key, null);
        assertEquals(first.get("data"), replay.get("data")); assertTrue(replay.at("/meta/replayed").asBoolean());
        assertEquals(1, student.get("/student/enrollments?semesterId=" + semester).get("totalSelected").asInt());
        var overview = teacher.get("/teacher/students?semesterId=" + semester + "&courseId=" + id);
        assertEquals(1, overview.get("total").asInt());
    }
    @Test void adminBootstrapAndRevokedSessionCannotRead() throws Exception {
        var admin = new Browser(); admin.login("admin", "admin");
        assertEquals("ADMIN", admin.get("/auth/me").get("role").asText());
        admin.post("/auth/logout", Map.of(), 200);
        admin.call("GET", "/auth/me", null, 401, null, null);
    }
}
