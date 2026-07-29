CREATE TABLE IF NOT EXISTS checkin_policies (
  semester_id CHAR(36) PRIMARY KEY,
  window_mode ENUM('semester_wide', 'specified_range') NOT NULL DEFAULT 'semester_wide',
  date_range_start DATE NULL,
  date_range_end DATE NULL,
  daily_start_time TIME NOT NULL,
  daily_end_time TIME NOT NULL,
  excluded_dates JSON NOT NULL,
  semester_deadline DATE NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_checkin_policies_semester FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
  CHECK (date_range_end IS NULL OR date_range_start IS NULL OR date_range_end >= date_range_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
