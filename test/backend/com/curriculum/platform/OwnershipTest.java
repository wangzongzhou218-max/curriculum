package com.curriculum.platform;

import com.curriculum.course.Courses;
import com.curriculum.identity.Identity;
import com.curriculum.platform.Models.Course;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipTest {
    @Test void ownershipUsesIdentifierValueBeyondLongCache() {
        Store db = mock(Store.class); Identity identity = mock(Identity.class);
        Course course = new Course(); course.teacherId = Long.valueOf("1000"); course.semesterId = 1L;
        when(db.get(Course.class, 9L)).thenReturn(course);
        var courses = new Courses(db, identity);
        assertSame(course, courses.own(new Identity.Actor("context", "session", Long.valueOf("1000")), 9L, 1L));
        assertThrows(Problem.class, () -> courses.own(new Identity.Actor("context", "session", 1001L), 9L, 1L));
    }
    @Test void auditRetainsOriginalCourseSnapshot() {
        Store db = new Store(); Course course = new Course(); course.id = 1000L; course.name = "原课程"; course.version = 1;
        db.beginAudit(); db.track(course); course.name = "新课程"; course.version = 2; db.track(course);
        var change = db.auditChanges().getFirst();
        assertEquals("原课程", change.before().get("name"));
        assertEquals("新课程", change.after().get("name"));
        db.endAudit(); assertTrue(db.auditChanges().isEmpty());
    }
}
