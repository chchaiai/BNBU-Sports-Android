ALTER TABLE proof_files
  MODIFY owner_type ENUM('upload', 'record', 'exemption', 'deleting') NOT NULL DEFAULT 'upload',
  ADD COLUMN cleanup_attempts INT UNSIGNED NOT NULL DEFAULT 0 AFTER owner_id,
  ADD COLUMN cleanup_last_attempt_at DATETIME(3) NULL AFTER cleanup_attempts,
  DROP INDEX ix_proof_owner,
  ADD KEY ix_proof_owner_created (owner_type, owner_id, created_at),
  ADD KEY ix_proof_cleanup_retry (owner_type, cleanup_last_attempt_at, created_at);
