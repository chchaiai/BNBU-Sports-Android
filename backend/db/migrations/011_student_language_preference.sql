ALTER TABLE users
  ADD COLUMN preferred_language ENUM('zh-CN', 'en') NOT NULL DEFAULT 'zh-CN' AFTER gender;
