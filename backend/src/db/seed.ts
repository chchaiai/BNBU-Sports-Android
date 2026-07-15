import "dotenv/config";
import { randomUUID } from "node:crypto";
import bcrypt from "bcryptjs";
import { loadConfig } from "../config/env";
import { createPool } from "./pool";

const ids = {
  student: "10000000-0000-4000-8000-000000000001",
  teacher: "10000000-0000-4000-8000-000000000002",
  semester: "20000000-0000-4000-8000-000000000001",
  course: "30000000-0000-4000-8000-000000000001",
  task: "40000000-0000-4000-8000-000000000001"
};

async function seed(): Promise<void> {
  const config = loadConfig();
  if (!config.SEED_STUDENT_ACCOUNT || !config.SEED_STUDENT_EMAIL || !config.SEED_STUDENT_PASSWORD) {
    throw new Error("SEED_STUDENT_ACCOUNT, SEED_STUDENT_EMAIL and SEED_STUDENT_PASSWORD are required");
  }
  const pool = createPool(config);
  try {
    const passwordHash = await bcrypt.hash(config.SEED_STUDENT_PASSWORD, 12);
    const disabledHash = await bcrypt.hash(randomUUID(), 12);
    await pool.execute(
      `INSERT INTO users
         (id, student_number, email, password_hash, role, name, college, class_name, gender, grade_level, admission_year, status)
       VALUES (?, ?, ?, ?, 'student', '演示学生', '通识教育学院', '2025级1班', 'male', 'sophomore', 2025, '正常')
       ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), email=VALUES(email), status='正常'`,
      [ids.student, config.SEED_STUDENT_ACCOUNT, config.SEED_STUDENT_EMAIL, passwordHash]
    );
    await pool.execute(
      `INSERT INTO users
         (id, student_number, email, password_hash, role, name, college, status)
       VALUES (?, 'seed-teacher', 'seed.teacher@example.invalid', ?, 'teacher', '体育教师', '体育部', '正常')
       ON DUPLICATE KEY UPDATE name=VALUES(name)`,
      [ids.teacher, disabledHash]
    );
    const year = new Date().getUTCFullYear();
    await pool.execute(
      `INSERT INTO semesters (id, name, academic_year, term, start_date, end_date, status)
       VALUES (?, ?, ?, '第一学期', ?, ?, 'current')
       ON DUPLICATE KEY UPDATE status='current', name=VALUES(name)`,
      [ids.semester, `${year}-${year + 1} 第一学期`, `${year}-${year + 1}`, `${year}-09-01`, `${year + 1}-01-31`]
    );
    await pool.execute(
      `INSERT INTO courses (id, semester_id, teacher_id, code, section, name, status)
       VALUES (?, ?, ?, 'GEPE101', '1004', '大学体育', 'active')
       ON DUPLICATE KEY UPDATE teacher_id=VALUES(teacher_id), name=VALUES(name)`,
      [ids.course, ids.semester, ids.teacher]
    );
    await pool.execute(
      `INSERT INTO course_enrollments (student_id, course_id, status) VALUES (?, ?, 'enrolled')
       ON DUPLICATE KEY UPDATE status='enrolled'`,
      [ids.student, ids.course]
    );
    await pool.execute(
      `INSERT INTO sport_tasks (id, course_id, title, description, credit_type, required_hours, deadline, status)
       VALUES (?, ?, '本周课程运动', '完成课程指定运动并上传凭证', '课程相关', 2.0, ?, '进行中')
       ON DUPLICATE KEY UPDATE deadline=VALUES(deadline), status='进行中'`,
      [ids.task, ids.course, `${year + 1}-01-15 23:59:59.000`]
    );
    await pool.execute(
      `INSERT INTO notifications (id, student_id, title, message, category, is_read)
       VALUES (?, ?, '欢迎使用 BNBU Sports', '请按任务要求完成体育打卡。', '系统通知', FALSE)
       ON DUPLICATE KEY UPDATE message=VALUES(message)`,
      ["50000000-0000-4000-8000-000000000001", ids.student]
    );
    await pool.execute(
      `INSERT INTO grades (id, student_id, course_id, checkin_score, exam_score, attendance_score, physical_score, total_score, source_trace)
       VALUES (?, ?, ?, 0, 0, 0, 0, 0, 'seed')
       ON DUPLICATE KEY UPDATE source_trace='seed'`,
      ["60000000-0000-4000-8000-000000000001", ids.student, ids.course]
    );

    const variants = [
      { gender: "male", group: "freshman_sophomore", run: "1000m", bands: [[120, 240, 100, "excellent"], [241, 270, 90, "good"], [271, 330, 80, "pass"], [331, 390, 60, "pass"], [391, 600, 40, "fail"]] },
      { gender: "male", group: "junior_senior", run: "1000m", bands: [[120, 250, 100, "excellent"], [251, 280, 90, "good"], [281, 340, 80, "pass"], [341, 400, 60, "pass"], [401, 600, 40, "fail"]] },
      { gender: "female", group: "freshman_sophomore", run: "800m", bands: [[120, 210, 100, "excellent"], [211, 240, 90, "good"], [241, 300, 80, "pass"], [301, 360, 60, "pass"], [361, 600, 40, "fail"]] },
      { gender: "female", group: "junior_senior", run: "800m", bands: [[120, 220, 100, "excellent"], [221, 250, 90, "good"], [251, 310, 80, "pass"], [311, 370, 60, "pass"], [371, 600, 40, "fail"]] }
    ] as const;
    for (const variant of variants) {
      for (const [min, max, score, tier] of variant.bands) {
        await pool.execute(
          `INSERT INTO endurance_scoring_rules
             (id, gender, grade_group, run_type, min_seconds, max_seconds, score, tier, note)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, '示例规则；上线前请由体育部门复核')
           ON DUPLICATE KEY UPDATE score=VALUES(score), tier=VALUES(tier), note=VALUES(note)`,
          [randomUUID(), variant.gender, variant.group, variant.run, min, max, score, tier]
        );
      }
    }
    console.info(`Seeded student account: ${config.SEED_STUDENT_ACCOUNT}`);
  } finally {
    await pool.end();
  }
}

void seed().catch((error) => {
  console.error(error);
  process.exit(1);
});
