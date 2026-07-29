-- An optional student-authored note, intentionally separate from exercise description.
ALTER TABLE sport_records
  ADD COLUMN remark VARCHAR(200) NOT NULL DEFAULT '' AFTER description;
