-- Preserve the session timing captured by the Android exercise timer.
-- Nullable columns keep historical records readable after deployment.
ALTER TABLE sport_records
  ADD COLUMN start_time DATETIME(3) NULL AFTER sport_type,
  ADD COLUMN end_time DATETIME(3) NULL AFTER start_time,
  ADD COLUMN actual_duration_seconds INT UNSIGNED NULL AFTER end_time;
