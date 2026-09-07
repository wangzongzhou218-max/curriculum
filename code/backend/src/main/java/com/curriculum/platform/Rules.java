package com.curriculum.platform;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public final class Rules {
    private Rules() {}
    public static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    public static final String[] COLORS = {"#F4A3A8", "#F6BD60", "#F6E27F", "#BDD77D", "#82C99A", "#7BC8BA", "#80CBE5", "#8FADE2", "#B3A0DD", "#D29BD4", "#E9A6C8", "#C3AD94"};
    public record Meeting(int weekday, String startTime, String endTime) {}
    public static String text(String value, String field, int max) {
        String result = value == null ? "" : value.strip();
        Problem.require(!result.isEmpty() && result.codePointCount(0, result.length()) <= max, 400, "VALIDATION_FAILED", field + "不能为空且不能超过" + max + "个字符");
        return result;
    }
    public static String password(String value, String confirmation) {
        Problem.require(value != null && !value.isBlank() && value.codePointCount(0, value.length()) >= 8 && value.codePointCount(0, value.length()) <= 64, 400, "VALIDATION_FAILED", "密码须为 8～64 个字符，且不能全为空白");
        Problem.require(value.equals(confirmation), 400, "VALIDATION_FAILED", "两次密码不一致");
        return value;
    }
    public static String email(String value) {
        String normalized = text(value, "邮箱", 254).toLowerCase(Locale.ROOT);
        Problem.require(Pattern.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+", normalized), 400, "VALIDATION_FAILED", "请输入有效邮箱");
        return normalized;
    }
    public static String registration(String value) {
        String result = text(value, "工号或学号", 32);
        Problem.require(result.matches("[A-Za-z0-9_-]+"), 400, "VALIDATION_FAILED", "编号只能包含字母、数字、短横线和下划线");
        return result;
    }
    public static int minute(String value) {
        Problem.require(value != null && value.matches("(?:[01][0-9]|2[0-3]):[03]0"), 400, "VALIDATION_FAILED", "授课时间须对齐半小时");
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
    }
    public static List<Meeting> meetings(List<Meeting> meetings) {
        Problem.require(meetings != null && !meetings.isEmpty() && meetings.size() <= 2, 400, "VALIDATION_FAILED", "每门课程须有一至两次授课安排");
        Set<Integer> days = new HashSet<>();
        for (Meeting meeting : meetings) {
            Problem.require(meeting != null, 400, "VALIDATION_FAILED", "授课安排不能为空");
            int start = minute(meeting.startTime()), end = minute(meeting.endTime());
            Problem.require(meeting.weekday() >= 1 && meeting.weekday() <= 5 && days.add(meeting.weekday()) && start < end && ((start >= 480 && end <= 720) || (start >= 840 && end <= 1080)), 400, "VALIDATION_FAILED", "请使用不同工作日的 08:00～12:00 或 14:00～18:00，不能跨午休");
        }
        return meetings.stream().sorted(Comparator.comparingInt(Meeting::weekday)).toList();
    }
    public static boolean conflicts(List<Meeting> left, List<Meeting> right) {
        return left.stream().anyMatch(a -> right.stream().anyMatch(b -> a.weekday() == b.weekday() && minute(a.startTime()) < minute(b.endTime()) && minute(b.startTime()) < minute(a.endTime())));
    }
    public static int academicYear(LocalDate date) { return date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1; }
    public static LocalDate start(int year, String season) { return "AUTUMN".equals(season) ? LocalDate.of(year, 9, 1) : LocalDate.of(year + 1, 3, 1); }
    public static boolean closed(LocalDate endExclusive, Instant now) { return !now.isBefore(endExclusive.atStartOfDay(SCHOOL_ZONE).toInstant()); }
    public static long id(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; } catch (RuntimeException ignored) { }
        throw new Problem(400, "VALIDATION_FAILED", "标识无效");
    }
}
