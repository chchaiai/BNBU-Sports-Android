ALTER TABLE grades
  ADD COLUMN endurance_run_time_seconds SMALLINT UNSIGNED NULL AFTER physical_score,
  ADD COLUMN endurance_run_status ENUM('recorded', 'exempt', 'absent', 'not_recorded') NOT NULL DEFAULT 'not_recorded' AFTER endurance_run_time_seconds,
  ADD CONSTRAINT chk_grades_endurance_run_time
    CHECK (endurance_run_time_seconds IS NULL OR endurance_run_time_seconds BETWEEN 1 AND 3600);
