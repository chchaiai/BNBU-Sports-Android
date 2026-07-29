DROP TABLE IF EXISTS record_supplements;
-- statement-breakpoint
ALTER TABLE sport_records
  DROP COLUMN approved_hours,
  DROP COLUMN status,
  DROP COLUMN review_comment,
  DROP COLUMN reviewed_at;
