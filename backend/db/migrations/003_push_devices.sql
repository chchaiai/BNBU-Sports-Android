-- Opaque FCM registration tokens are device addresses, not message content.
-- The token hash provides a bounded unique index; the raw token is needed only
-- by the trusted FCM sender and must never be returned from the student API.
CREATE TABLE IF NOT EXISTS push_devices (
  id CHAR(36) PRIMARY KEY,
  student_id CHAR(36) NOT NULL,
  platform ENUM('android') NOT NULL,
  token TEXT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  app_version VARCHAR(64) NOT NULL,
  last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  invalidated_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uq_push_devices_token_hash (token_hash),
  KEY ix_push_devices_student_active (student_id, invalidated_at, last_seen_at DESC),
  CONSTRAINT fk_push_devices_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
