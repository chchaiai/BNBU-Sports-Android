-- Keeps feedback attachments under the same ownership and cleanup lifecycle as
-- other authenticated student uploads.
ALTER TABLE proof_files
  MODIFY owner_type ENUM('upload', 'record', 'exemption', 'feedback') NOT NULL DEFAULT 'upload';
-- statement-breakpoint
CREATE TABLE IF NOT EXISTS feedback_tickets (
  id CHAR(36) PRIMARY KEY,
  ticket_number VARCHAR(32) NOT NULL UNIQUE,
  student_id CHAR(36) NOT NULL,
  category VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  current_page VARCHAR(256) NOT NULL,
  client_version VARCHAR(64) NOT NULL,
  contact_email VARCHAR(254) NULL,
  contact_phone VARCHAR(32) NULL,
  status ENUM('pending', 'processing', 'resolved', 'closed') NOT NULL DEFAULT 'pending',
  reply TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY ix_feedback_tickets_student_created (student_id, created_at DESC),
  CONSTRAINT fk_feedback_tickets_student
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
