package com.curriculum.platform;

import com.curriculum.identity.Identity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

@Configuration
public class SecurityConfig {
    public static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public static void setCookie(HttpServletResponse response, String name, String value, boolean httpOnly, boolean secure, long age) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(httpOnly).secure(secure).sameSite("Lax").path("/").maxAge(age).build().toString());
    }
    @Bean SecurityFilterChain security(HttpSecurity http, Crypto crypto, Identity identity, ObjectMapper mapper, @Value("${app.origin}") String origin) throws Exception {
        return http.csrf(c -> c.csrfTokenRepository(new BoundCsrfRepository(crypto))
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .exceptionHandling(c -> c.accessDeniedHandler((request, response, ex) -> write(response, mapper,
                new Problem(403, "CSRF_INVALID", "页面验证已失效，请刷新后重试"), UUID.randomUUID().toString())))
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c.anyRequest().permitAll())
            .addFilterBefore(new ApiFilter(crypto, identity, mapper, origin), UsernamePasswordAuthenticationFilter.class).build();
    }
    static class BoundCsrfRepository implements CsrfTokenRepository {
        private final Crypto crypto;
        BoundCsrfRepository(Crypto crypto) { this.crypto = crypto; }
        public CsrfToken generateToken(HttpServletRequest request) { return new DefaultCsrfToken("X-CSRF-Token", "_csrf", UUID.randomUUID().toString()); }
        public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) { /* Issued only by GET /auth/context. */ }
        public CsrfToken loadToken(HttpServletRequest request) {
            String token = cookie(request, "CURRICULUM_CSRF"), context = crypto.verify("context", cookie(request, "CURRICULUM_CONTEXT"));
            String content = crypto.verify("csrf", token);
            return context != null && content != null && content.startsWith(context + ":") ? new DefaultCsrfToken("X-CSRF-Token", "_csrf", token) : null;
        }
    }
    static class ApiFilter extends OncePerRequestFilter {
        private final Crypto crypto;
        private final Identity identity;
        private final ObjectMapper mapper;
        private final String origin;
        ApiFilter(Crypto crypto, Identity identity, ObjectMapper mapper, String origin) { this.crypto = crypto; this.identity = identity; this.mapper = mapper; this.origin = origin; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String requestId = UUID.randomUUID().toString();
            request.setAttribute("requestId", requestId);
            response.setHeader("X-Request-Id", requestId);
            response.setHeader("Cache-Control", "no-store");
            if (!request.getRequestURI().startsWith("/api/")) { chain.doFilter(request, response); return; }
            try {
                Problem.require(request.getContentLengthLong() <= 65536, 413, "VALIDATION_FAILED", "请求内容过大");
                String context = crypto.verify("context", cookie(request, "CURRICULUM_CONTEXT"));
                String session = crypto.verify("session", cookie(request, "CURRICULUM_SESSION"));
                var actor = identity.identify(context, session);
                request.setAttribute("actor", actor);
                if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                    String token = cookie(request, "CURRICULUM_CSRF");
                    String content = crypto.verify("csrf", token);
                    Problem.require(actor.contextId() != null && content != null && content.startsWith(actor.contextId() + ":")
                        && Crypto.equal(token, request.getHeader("X-CSRF-Token")) && origin.equals(request.getHeader("Origin")), 403, "CSRF_INVALID", "页面验证已失效，请刷新后重试");
                }
                chain.doFilter(request, response);
            } catch (Problem problem) { write(response, mapper, problem, requestId); }
            catch (org.springframework.dao.DataAccessException problem) { write(response, mapper, new Problem(503, "SERVICE_UNAVAILABLE", "数据库暂不可用，请稍后重试"), requestId); }
        }
    }
    public static Map<String, Object> error(Problem problem, String requestId) {
        return Map.of("error", Map.of("code", problem.code, "message", problem.getMessage(), "details", problem.details), "meta", Map.of("requestId", requestId));
    }
    private static void write(HttpServletResponse response, ObjectMapper mapper, Problem problem, String requestId) throws IOException {
        response.setStatus(problem.status); response.setContentType("application/json;charset=UTF-8");
        if (problem.status == 429) response.setHeader("Retry-After", "60");
        mapper.writeValue(response.getOutputStream(), error(problem, requestId));
    }
}
