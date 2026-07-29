-- A login identifier is not automatically a verified recovery contact.  These
-- fields hold only contacts the student has explicitly verified after joining
-- a course.
ALTER TABLE users
  ADD COLUMN contact_email VARCHAR(254) NULL AFTER email,
  ADD COLUMN contact_email_verified_at DATETIME(3) NULL AFTER contact_email,
  ADD COLUMN contact_phone VARCHAR(32) NULL AFTER contact_email_verified_at,
  ADD COLUMN contact_phone_verified_at DATETIME(3) NULL AFTER contact_phone,
  ADD UNIQUE KEY uq_users_contact_email (contact_email),
  ADD UNIQUE KEY uq_users_contact_phone (contact_phone);
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS contact_verification_codes (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  channel ENUM('email', 'phone') NOT NULL,
  contact_value VARCHAR(254) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_contact_codes_active (student_id, channel, contact_value, consumed_at, created_at DESC),
  CONSTRAINT fk_contact_codes_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
