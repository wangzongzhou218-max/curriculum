package com.curriculum.platform;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RulesTest {
    Rules.Meeting meeting(int day, String start, String end) { return new Rules.Meeting(day, start, end); }
    @Test void adjacentMeetingsDoNotConflict() {
        var first = List.of(meeting(1, "08:00", "10:00"));
        assertFalse(Rules.conflicts(first, List.of(meeting(1, "10:00", "12:00"))));
        assertTrue(Rules.conflicts(first, List.of(meeting(1, "09:30", "10:30"))));
        assertFalse(Rules.conflicts(first, List.of(meeting(2, "08:00", "10:00"))));
    }
    @Test void rejectsLunchWeekendRepeatedDayAndOffGridTimes() {
        for (var item : List.of(meeting(1, "11:00", "14:30"), meeting(6, "08:00", "09:00"), meeting(1, "08:15", "09:00"), meeting(1, "10:00", "09:00")))
            assertThrows(Problem.class, () -> Rules.meetings(List.of(item)));
        assertThrows(Problem.class, () -> Rules.meetings(List.of(meeting(1, "08:00", "09:00"), meeting(1, "14:00", "15:00"))));
        assertEquals(2, Rules.meetings(List.of(meeting(1, "08:00", "12:00"), meeting(5, "14:00", "18:00"))).size());
    }
    @Test void usesSchoolTimezoneAndExclusiveSemesterEnd() {
        var end = LocalDate.of(2026, 11, 24);
        var boundary = end.atStartOfDay(Rules.SCHOOL_ZONE).toInstant();
        assertFalse(Rules.closed(end, boundary.minusNanos(1)));
        assertTrue(Rules.closed(end, boundary));
        assertEquals(2025, Rules.academicYear(LocalDate.of(2026, 8, 31)));
        assertEquals(2026, Rules.academicYear(LocalDate.of(2026, 9, 1)));
        assertEquals(LocalDate.of(2027, 3, 1), Rules.start(2026, "SPRING"));
    }
    @Test void preservesPasswordWhitespaceAndCountsUnicodeCodepoints() {
        String password = " 😀😀😀😀😀😀 ";
        assertEquals(password, Rules.password(password, password));
        assertThrows(Problem.class, () -> Rules.password(password, password.strip()));
        assertThrows(Problem.class, () -> Rules.password("        ", "        "));
        assertThrows(Problem.class, () -> Rules.password("😀".repeat(65), "😀".repeat(65)));
        assertEquals("a@example.edu", Rules.email(" A@Example.EDU "));
        assertThrows(Problem.class, () -> Rules.registration("学生001"));
    }
}
