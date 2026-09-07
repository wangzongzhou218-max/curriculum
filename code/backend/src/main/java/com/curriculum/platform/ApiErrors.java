package com.curriculum.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(Problem.class) public ResponseEntity<?> problem(Problem problem, HttpServletRequest request) {
        var response = ResponseEntity.status(problem.status);
        if (problem.status == 429) response.header("Retry-After", "60");
        return response.body(SecurityConfig.error(problem, Objects.toString(request.getAttribute("requestId"), UUID.randomUUID().toString())));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid(Exception ex, HttpServletRequest request) { return problem(new Problem(400, "VALIDATION_FAILED", "请求字段或格式无效"), request); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) public ResponseEntity<?> database(Exception ex, HttpServletRequest request) { return problem(new Problem(503, "SERVICE_UNAVAILABLE", "数据库暂不可用，请稍后查询操作结果"), request); }
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("requestId={} type={}", request.getAttribute("requestId"), ex.getClass().getSimpleName());
        return problem(new Problem(500, "INTERNAL_ERROR", "服务发生异常，请凭排查号联系维护人员"), request);
    }
}
