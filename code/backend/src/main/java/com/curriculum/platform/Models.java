package com.curriculum.platform;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** Persistence records stay inside the service boundary; controllers return explicit views. */
public final class Models {
    private Models() {}
    @Entity(name = "Account") @Table(name = "app_user")
    public static class Account {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(length = 8, nullable = false) public String account;
        @Column(length = 16, nullable = false) public String role;
        @Column(length = 50, nullable = false) public String name;
        @Column(length = 32) public String registrationNumber;
        @Column(length = 254) public String emailNormalized;
        @Column(length = 255, nullable = false) public String passwordHash;
        @Column(length = 16, nullable = false) public String status = "ACTIVE";
        public long authVersion = 1;
        public long version = 1;
        public Instant createdAt;
        public Instant updatedAt;
        public Instant deletedAt;
    }
    @Entity(name = "Semester") @Table(name = "semester")
    public static class Semester {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public int academicYear;
        @Column(length = 8) public String season;
        public LocalDate startsOn;
        public LocalDate endsOnExclusive;
        public Instant createdAt;
    }
    @Entity(name = "Course") @Table(name = "course")
    public static class Course {
        @Id public Long id;
        @Column(length = 24) public String code;
        public int paletteSlot;
        public long semesterId;
        public long teacherId;
        @Column(length = 100) public String name;
        @Column(length = 2000) public String description;
        @Column(length = 16) public String publication = "DRAFT";
        public long version = 1;
        public Instant createdAt;
        public Instant updatedAt;
        public Instant deletedAt;
        @ElementCollection(fetch = FetchType.EAGER)
        @CollectionTable(name = "course_meeting", joinColumns = @JoinColumn(name = "course_id"))
        @OrderBy("weekday ASC") public List<CourseMeeting> meetings = new ArrayList<>();
    }
    @Embeddable public static class CourseMeeting {
        public int weekday;
        public int startMinute;
        public int endMinute;
        public CourseMeeting() {}
        public CourseMeeting(Rules.Meeting input) { weekday = input.weekday(); startMinute = Rules.minute(input.startTime()); endMinute = Rules.minute(input.endTime()); }
        public Rules.Meeting view() { return new Rules.Meeting(weekday, time(startMinute), time(endMinute)); }
        private static String time(int minutes) { return "%02d:%02d".formatted(minutes / 60, minutes % 60); }
    }
    @Entity(name = "Enrollment") @Table(name = "enrollment")
    public static class Enrollment {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public long studentId;
        public long courseId;
        public long semesterId;
        @Column(length = 20) public String state = "ACTIVE";
        public long generation = 1;
        public long version = 1;
        public Instant enrolledAt;
        public Instant endedAt;
        public Instant updatedAt;
    }
    @Entity(name = "BrowserContext") @Table(name = "browser_context")
    public static class BrowserContext {
        @Id @Column(length = 36) public String id;
        public Instant createdAt;
        public Instant expiresAt;
    }
    @Entity(name = "AuthSession") @Table(name = "auth_session")
    public static class AuthSession {
        @Id @Column(length = 36) public String id;
        public long userId;
        @Column(length = 36) public String contextId;
        public long authVersion;
        public Instant issuedAt;
        public Instant lastInteractiveAt;
        public Instant absoluteExpiresAt;
        public Instant revokedAt;
    }
    @Entity(name = "Receipt") @Table(name = "operation_receipt")
    public static class Receipt {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(length = 80) public String scope;
        @Column(length = 36) public String operationKey;
        @Column(length = 64) public String requestDigest;
        public int httpStatus;
        @Column(length = 16) public String outcome;
        @Column(columnDefinition = "text") public String resultData;
        public Instant createdAt;
        public Instant expiresAt;
    }
    @Entity(name = "DomainEvent") @Table(name = "domain_event")
    public static class DomainEvent {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long actorId;
        public long operationId;
        @Column(length = 40) public String eventType;
        @Column(length = 160) public String target;
        @Column(length = 24) public String entityType;
        public Long entityId;
        public Long semesterId;
        @Column(columnDefinition = "text") public String beforeData;
        @Column(columnDefinition = "text") public String afterData;
        public Instant occurredAt;
    }
}
