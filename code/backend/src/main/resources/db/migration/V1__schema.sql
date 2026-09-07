CREATE TABLE app_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, account VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 role VARCHAR(16) NOT NULL, name VARCHAR(50) NOT NULL,
 registration_number VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
 email_normalized VARCHAR(254) COLLATE utf8mb4_bin, password_hash VARCHAR(255) NOT NULL,
 status VARCHAR(16) NOT NULL, auth_version BIGINT NOT NULL, version BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, deleted_at DATETIME(6),
 UNIQUE(role, registration_number), UNIQUE(email_normalized), INDEX(role,status,account), INDEX(role,status,created_at,id),
 CHECK (role IN ('ADMIN','TEACHER','STUDENT')),
 CHECK ((role='ADMIN' AND account='admin' AND registration_number IS NULL AND email_normalized IS NULL) OR (role='TEACHER' AND REGEXP_LIKE(account,'^t[0-9]{7}$','c') AND registration_number IS NOT NULL AND email_normalized IS NOT NULL) OR (role='STUDENT' AND REGEXP_LIKE(account,'^s[0-9]{7}$','c') AND registration_number IS NOT NULL AND email_normalized IS NOT NULL)),
 CHECK ((status='ACTIVE' AND deleted_at IS NULL) OR (status='DELETED' AND deleted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
CREATE TABLE semester (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, academic_year INT NOT NULL, season VARCHAR(8) NOT NULL,
 starts_on DATE NOT NULL UNIQUE, ends_on_exclusive DATE NOT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE(academic_year,season), CHECK (DATEDIFF(ends_on_exclusive,starts_on)=84), CHECK (season IN ('AUTUMN','SPRING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
CREATE TABLE command_guard (id TINYINT PRIMARY KEY, revision BIGINT NOT NULL);
INSERT INTO command_guard VALUES (1,0);
CREATE TABLE number_sequence (name VARCHAR(32) PRIMARY KEY, next_value BIGINT NOT NULL);
INSERT INTO number_sequence VALUES ('COURSE',1);
CREATE TABLE course (
 id BIGINT PRIMARY KEY, code VARCHAR(24) NOT NULL UNIQUE, palette_slot INT NOT NULL,
 semester_id BIGINT NOT NULL, teacher_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, description VARCHAR(2000) NOT NULL,
 publication VARCHAR(16) NOT NULL, version BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, deleted_at DATETIME(6),
 UNIQUE(id,semester_id), FOREIGN KEY(semester_id) REFERENCES semester(id), FOREIGN KEY(teacher_id) REFERENCES app_user(id),
 CHECK(palette_slot BETWEEN 0 AND 11), CHECK(publication IN ('DRAFT','PUBLISHED','UNPUBLISHED')),
 INDEX(teacher_id,semester_id,deleted_at,created_at,id), INDEX(semester_id,publication,deleted_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
CREATE TABLE course_meeting (
 course_id BIGINT NOT NULL, weekday INT NOT NULL, start_minute INT NOT NULL, end_minute INT NOT NULL,
 PRIMARY KEY(course_id,weekday), FOREIGN KEY(course_id) REFERENCES course(id),
 CHECK(weekday BETWEEN 1 AND 5), CHECK(MOD(start_minute,30)=0 AND MOD(end_minute,30)=0 AND start_minute < end_minute),
 CHECK((start_minute>=480 AND end_minute<=720) OR (start_minute>=840 AND end_minute<=1080))
) ENGINE=InnoDB;
CREATE TABLE enrollment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, student_id BIGINT NOT NULL, course_id BIGINT NOT NULL, semester_id BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL, generation BIGINT NOT NULL, version BIGINT NOT NULL, enrolled_at DATETIME(6) NOT NULL, ended_at DATETIME(6), updated_at DATETIME(6) NOT NULL,
 UNIQUE(student_id,course_id), FOREIGN KEY(student_id) REFERENCES app_user(id), FOREIGN KEY(course_id,semester_id) REFERENCES course(id,semester_id),
 INDEX(student_id,semester_id,state,course_id), INDEX(course_id,state,student_id),
 CHECK((state='ACTIVE' AND ended_at IS NULL) OR (state IN ('DROPPED','SWAPPED_OUT','ACCOUNT_DELETED') AND ended_at IS NOT NULL))
) ENGINE=InnoDB;
CREATE TABLE browser_context (id VARCHAR(36) PRIMARY KEY, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL) ENGINE=InnoDB;
CREATE TABLE auth_session (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, context_id VARCHAR(36) NOT NULL, auth_version BIGINT NOT NULL,
 issued_at DATETIME(6) NOT NULL, last_interactive_at DATETIME(6) NOT NULL, absolute_expires_at DATETIME(6) NOT NULL, revoked_at DATETIME(6),
 FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(context_id) REFERENCES browser_context(id), INDEX(user_id,revoked_at)
) ENGINE=InnoDB;
CREATE TABLE operation_receipt (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, scope VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, request_digest VARCHAR(64) NOT NULL,
 http_status INT NOT NULL, outcome VARCHAR(16) NOT NULL, result_data TEXT,
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL, UNIQUE(scope,operation_key), INDEX(expires_at)
) ENGINE=InnoDB;
CREATE TABLE domain_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_id BIGINT, operation_id BIGINT NOT NULL, event_type VARCHAR(40) NOT NULL,
 target VARCHAR(160) NOT NULL, occurred_at DATETIME(6) NOT NULL, entity_type VARCHAR(24), entity_id BIGINT, semester_id BIGINT,
 before_data TEXT, after_data TEXT, FOREIGN KEY(operation_id) REFERENCES operation_receipt(id), INDEX(entity_type,entity_id,id)
) ENGINE=InnoDB;
CREATE TABLE rate_bucket (
 bucket_type VARCHAR(20) NOT NULL, key_digest VARCHAR(64) NOT NULL, window_start BIGINT NOT NULL, request_count INT NOT NULL,
 PRIMARY KEY(bucket_type,key_digest,window_start)
) ENGINE=InnoDB;
