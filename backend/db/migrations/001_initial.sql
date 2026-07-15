CREATE TABLE IF NOT EXISTS users (
  id CHAR(36) PRIMARY KEY,
  student_number VARCHAR(64) NOT NULL UNIQUE,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('student', 'teacher', 'admin') NOT NULL,
  name VARCHAR(128) NOT NULL,
  college VARCHAR(255) NOT NULL DEFAULT '',
  class_name VARCHAR(128) NOT NULL DEFAULT '',
  gender ENUM('male', 'female') NULL,
  grade_level ENUM('freshman', 'sophomore', 'junior', 'senior') NULL,
  admission_year SMALLINT UNSIGNED NULL,
  status VARCHAR(32) NOT NULL DEFAULT '正常',
  token_version INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CHECK (admission_year IS NULL OR admission_year BETWEEN 2000 AND 2200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS semesters (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  academic_year VARCHAR(16) NOT NULL,
  term VARCHAR(32) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status ENUM('current', 'archived', 'upcoming') NOT NULL DEFAULT 'upcoming',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS courses (
  id CHAR(36) PRIMARY KEY,
  semester_id CHAR(36) NOT NULL,
  teacher_id CHAR(36) NULL,
  code VARCHAR(64) NOT NULL,
  section VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_course_semester_section (semester_id, code, section),
  CONSTRAINT fk_courses_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
  CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS course_enrollments (
  student_id CHAR(36) NOT NULL,
  course_id CHAR(36) NOT NULL,
  status ENUM('enrolled', 'completed', 'withdrawn') NOT NULL DEFAULT 'enrolled',
  enrolled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (student_id, course_id),
  CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS sport_tasks (
  id CHAR(36) PRIMARY KEY,
  course_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  credit_type ENUM('课程相关', '其他运动') NOT NULL,
  required_hours DECIMAL(4,1) NOT NULL,
  deadline DATETIME(3) NOT NULL,
  status ENUM('草稿', '进行中', '已关闭') NOT NULL DEFAULT '进行中',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_tasks_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CHECK (required_hours IN (1.0, 2.0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS sport_records (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  course_id CHAR(36) NULL,
  task_id CHAR(36) NULL,
  credit_type ENUM('课程相关', '其他运动', '系统抵扣') NOT NULL,
  hours DECIMAL(4,1) NOT NULL,
  approved_hours DECIMAL(4,1) NOT NULL DEFAULT 0,
  description TEXT NOT NULL,
  sport_type VARCHAR(100) NULL,
  status ENUM('待审核', '已通过', '已驳回', '补材料', '系统抵扣') NOT NULL DEFAULT '待审核',
  review_comment TEXT NULL,
  ai_review_status ENUM('pending', 'normal', 'abnormal', 'manual_review') NULL,
  ai_risk_level ENUM('low', 'medium', 'high') NULL,
  ai_risk_codes JSON NOT NULL,
  ai_review_message TEXT NULL,
  ai_confidence DECIMAL(5,4) NULL,
  ai_reviewed_at DATETIME(3) NULL,
  reviewed_at DATETIME(3) NULL,
  submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  submission_date DATE NOT NULL,
  source ENUM('student', 'system') NOT NULL DEFAULT 'student',
  daily_guard VARCHAR(80) GENERATED ALWAYS AS (
    IF(source = 'student', CONCAT(student_id, '#', submission_date), NULL)
  ) STORED,
  UNIQUE KEY uq_student_daily_submission (daily_guard),
  UNIQUE KEY uq_student_task_submission (student_id, task_id),
  KEY ix_records_student_submitted (student_id, submitted_at DESC),
  KEY ix_records_task (task_id),
  CONSTRAINT fk_records_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_records_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL,
  CONSTRAINT fk_records_task FOREIGN KEY (task_id) REFERENCES sport_tasks(id) ON DELETE SET NULL,
  CHECK (hours IN (1.0, 2.0)),
  CHECK (approved_hours BETWEEN 0 AND hours)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS proof_files (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  cos_key VARCHAR(1024) NOT NULL UNIQUE,
  url VARCHAR(4096) NOT NULL,
  media_type ENUM('image', 'video') NOT NULL,
  mime_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  owner_type ENUM('upload', 'record', 'exemption') NOT NULL DEFAULT 'upload',
  owner_id CHAR(36) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_proof_owner (owner_type, owner_id),
  CONSTRAINT fk_proofs_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CHECK ((media_type = 'image' AND size_bytes <= 8000000) OR (media_type = 'video' AND size_bytes <= 100000000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS record_supplements (
  id CHAR(36) PRIMARY KEY,
  record_id CHAR(36) NOT NULL,
  student_id CHAR(36) NOT NULL,
  hours DECIMAL(4,1) NOT NULL,
  description TEXT NOT NULL,
  proof_keys JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_supplements_record FOREIGN KEY (record_id) REFERENCES sport_records(id) ON DELETE CASCADE,
  CONSTRAINT fk_supplements_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CHECK (hours IN (1.0, 2.0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS memberships (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  student_name VARCHAR(128) NOT NULL,
  type ENUM('team', 'club') NOT NULL,
  organization VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '待确认',
  valid_until DATE NULL,
  offset VARCHAR(32) NOT NULL DEFAULT '待确认',
  comment TEXT NULL,
  updated_by VARCHAR(128) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_memberships_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS notifications (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  category ENUM('截止提醒', '申请与材料', '审核反馈', '组织认证', '系统通知') NOT NULL DEFAULT '系统通知',
  target_type VARCHAR(64) NULL,
  target_id CHAR(36) NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  read_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_notifications_student_created (student_id, created_at DESC),
  CONSTRAINT fk_notifications_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS exemptions (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  type ENUM('800m', '1000m', 'team', 'club') NOT NULL,
  category ENUM('physical_test', 'checkin') NOT NULL,
  organization VARCHAR(128) NULL,
  reason TEXT NOT NULL,
  status ENUM('pending', 'reviewing', 'supplement_required', 'approved', 'rejected', 'expired') NOT NULL DEFAULT 'pending',
  review_comment TEXT NULL,
  reviewer_id CHAR(36) NULL,
  reviewer_name VARCHAR(128) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY ix_exemptions_student_created (student_id, created_at DESC),
  CONSTRAINT fk_exemptions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_exemptions_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE SET NULL,
  CHECK ((category = 'physical_test' AND type IN ('800m', '1000m') AND organization IS NULL)
      OR (category = 'checkin' AND type IN ('team', 'club') AND organization IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS grades (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  course_id CHAR(36) NULL,
  checkin_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  exam_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  attendance_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  physical_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  total_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  source_trace VARCHAR(512) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_grade_student_course (student_id, course_id),
  CONSTRAINT fk_grades_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_grades_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CHECK (checkin_score <= 100 AND exam_score <= 100 AND attendance_score <= 100 AND physical_score <= 100 AND total_score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS exemption_supplements (
  id CHAR(36) PRIMARY KEY,
  exemption_id CHAR(36) NOT NULL,
  student_id CHAR(36) NOT NULL,
  reason TEXT NOT NULL,
  organization VARCHAR(128) NULL,
  proof_keys JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_exemption_supplements (exemption_id, created_at DESC),
  CONSTRAINT fk_exemption_supplements_application FOREIGN KEY (exemption_id) REFERENCES exemptions(id) ON DELETE CASCADE,
  CONSTRAINT fk_exemption_supplements_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS endurance_scoring_rules (
  id CHAR(36) PRIMARY KEY,
  gender ENUM('male', 'female') NOT NULL,
  grade_group ENUM('freshman_sophomore', 'junior_senior') NOT NULL,
  run_type ENUM('800m', '1000m') NOT NULL,
  min_seconds SMALLINT UNSIGNED NOT NULL,
  max_seconds SMALLINT UNSIGNED NOT NULL,
  score SMALLINT UNSIGNED NOT NULL,
  tier ENUM('excellent', 'good', 'pass', 'fail') NOT NULL,
  note VARCHAR(255) NULL,
  UNIQUE KEY uq_endurance_range (gender, grade_group, run_type, min_seconds, max_seconds),
  CHECK (max_seconds >= min_seconds AND score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS idempotency_keys (
  student_id CHAR(36) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  scope VARCHAR(160) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (student_id, idempotency_key, scope),
  CONSTRAINT fk_idempotency_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  actor_id CHAR(36) NULL,
  action VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NULL,
  request_id VARCHAR(128) NULL,
  metadata JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_audit_actor_created (actor_id, created_at DESC),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
