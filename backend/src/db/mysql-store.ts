import { randomUUID } from "node:crypto";
import type { Pool, PoolConnection, ResultSetHeader, RowDataPacket } from "mysql2/promise";
import { AppError } from "../errors";
import type { BackendStore, JsonObject } from "../store";
import type {
  CreateExemptionInput,
  CreateRecordInput,
  ProofFile,
  SupplementRecordInput,
  SupplementExemptionInput,
  UserRecord
} from "../types";

type Queryable = Pick<Pool, "execute"> | Pick<PoolConnection, "execute">;

function rows<T extends RowDataPacket = RowDataPacket>(result: [T[], unknown]): T[] {
  return result[0];
}

function first<T extends RowDataPacket = RowDataPacket>(result: [T[], unknown]): T | null {
  return result[0][0] ?? null;
}

function iso(value: unknown): string | null {
  if (value == null) return null;
  if (value instanceof Date) return value.toISOString();
  const text = String(value);
  if (!text) return null;
  const normalized = text.includes("T") ? text : text.replace(" ", "T") + "Z";
  const parsed = new Date(normalized);
  return Number.isNaN(parsed.valueOf()) ? text : parsed.toISOString();
}

function jsonArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String);
  if (typeof value !== "string" || !value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function chineseBusinessDate(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(new Date());
}

function academicYearStart(date = new Date()): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "numeric"
  }).formatToParts(date);
  const year = Number(parts.find((part) => part.type === "year")?.value);
  const month = Number(parts.find((part) => part.type === "month")?.value);
  return month >= 9 ? year : year - 1;
}

function currentGrade(admissionYear: number | null, fallback: string | null, startYear: number): string {
  if (!admissionYear) return fallback ?? "";
  const offset = startYear - admissionYear;
  return ["freshman", "sophomore", "junior", "senior"][Math.max(0, Math.min(offset, 3))] ?? "senior";
}

function mysqlCode(error: unknown): string {
  return typeof error === "object" && error !== null && "code" in error ? String((error as { code: unknown }).code) : "";
}

function mysqlMessage(error: unknown): string {
  return error instanceof Error ? error.message : "";
}

function requiredIdempotencyHash(value: string | undefined): string {
  if (!value || !/^[a-f0-9]{64}$/.test(value)) {
    throw new AppError(500, "IDEMPOTENCY_HASH_MISSING", "服务器未能生成幂等请求指纹");
  }
  return value;
}

export class MysqlStore implements BackendStore {
  constructor(private readonly pool: Pool) {}

  async ping(): Promise<boolean> {
    try {
      const migration = first(await this.pool.execute<RowDataPacket[]>(
        `SELECT COUNT(*) AS count FROM schema_migrations
          WHERE name IN ('001_initial.sql', '002_proof_cleanup_state.sql')`
      ));
      await this.pool.query("SELECT 1 FROM users LIMIT 0");
      return Number(migration?.count ?? 0) === 2;
    } catch {
      return false;
    }
  }

  async close(): Promise<void> {
    await this.pool.end();
  }

  async findUserByAccount(account: string): Promise<UserRecord | null> {
    const row = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT id, student_number AS studentNumber, email, password_hash AS passwordHash,
              role, name, college, class_name AS className, gender, grade_level AS gradeLevel,
              admission_year AS admissionYear, status, token_version AS tokenVersion
         FROM users
        WHERE student_number = ? OR LOWER(email) = LOWER(?)
        LIMIT 1`,
      [account, account]
    ));
    return row ? row as UserRecord : null;
  }

  async findUserById(id: string): Promise<UserRecord | null> {
    const row = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT id, student_number AS studentNumber, email, password_hash AS passwordHash,
              role, name, college, class_name AS className, gender, grade_level AS gradeLevel,
              admission_year AS admissionYear, status, token_version AS tokenVersion
         FROM users WHERE id = ? LIMIT 1`,
      [id]
    ));
    return row ? row as UserRecord : null;
  }

  async getProfile(studentId: string): Promise<JsonObject> {
    const user = await this.requireUser(studentId);
    const enrollment = first(await this.pool.execute<RowDataPacket[]>(
      "SELECT COUNT(*) AS count FROM course_enrollments WHERE student_id = ? AND status = 'enrolled'",
      [studentId]
    ));
    const year = academicYearStart();
    return {
      id: user.id,
      name: user.name,
      email: user.email,
      role: user.role,
      college: user.college,
      className: user.className,
      gender: user.gender,
      gradeLevel: user.gradeLevel,
      admissionYear: user.admissionYear,
      currentGradeLevel: currentGrade(user.admissionYear, user.gradeLevel, year),
      currentAcademicYear: `${year}-${year + 1}`,
      gradeCalculatedAt: new Date().toISOString(),
      status: user.status,
      enrolledCourses: Number(enrollment?.count ?? 0)
    };
  }

  async updateProfile(studentId: string, gender: "male" | "female" | null): Promise<JsonObject> {
    await this.pool.execute("UPDATE users SET gender = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?", [gender, studentId]);
    return this.getProfile(studentId);
  }

  async getSportSummary(studentId: string): Promise<JsonObject> {
    const semester = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT id, start_date, end_date FROM semesters
        WHERE status = 'current' ORDER BY start_date DESC LIMIT 1`
    ));
    if (!semester) {
      return {
        courseHours: 0,
        generalHours: 0,
        totalCompleted: 0,
        totalRequired: 20,
        totalRemaining: 20,
        courseRemaining: 10,
        generalRemaining: 10,
        completed: false,
        pendingCount: 0,
        rule: { total: 20, courseRequired: 10, generalRequired: 10, dailyLimit: 2 },
        teachers: [],
        courses: []
      };
    }
    const totals = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT COALESCE(SUM(CASE WHEN r.credit_type = '课程相关' AND r.status = '已通过' THEN r.approved_hours ELSE 0 END), 0) AS courseHours,
              COALESCE(SUM(CASE WHEN r.credit_type = '其他运动' AND r.status = '已通过' THEN r.approved_hours ELSE 0 END), 0) AS generalHours,
              SUM(CASE WHEN r.status = '待审核' THEN 1 ELSE 0 END) AS pendingCount
         FROM sport_records r
         LEFT JOIN courses c ON c.id = r.course_id
        WHERE r.student_id = ? AND (
          (r.credit_type = '课程相关' AND c.semester_id = ?)
          OR (r.credit_type = '其他运动' AND r.submission_date BETWEEN ? AND ?)
        )`,
      [studentId, semester.id, semester.start_date, semester.end_date]
    ));
    const courseHours = Number(totals?.courseHours ?? 0);
    const generalHours = Number(totals?.generalHours ?? 0);
    const totalCompleted = Math.min(courseHours, 10) + Math.min(generalHours, 10);
    const courseRows = await this.courseRows(studentId, "current");
    const courseTotals = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT r.course_id,
              COALESCE(SUM(CASE WHEN r.credit_type = '课程相关' AND r.status = '已通过' THEN r.approved_hours ELSE 0 END), 0) AS courseHours,
              COALESCE(SUM(CASE WHEN r.credit_type = '其他运动' AND r.status = '已通过' THEN r.approved_hours ELSE 0 END), 0) AS generalHours
         FROM sport_records r
         JOIN courses c ON c.id = r.course_id
        WHERE r.student_id = ? AND c.semester_id = ?
        GROUP BY r.course_id`,
      [studentId, semester.id]
    ));
    const totalsByCourse = new Map(courseTotals.map((row) => [String(row.course_id), row]));
    const courses = courseRows.map((course) => ({
      courseId: course.id,
      courseCode: course.code,
      courseSection: course.section,
      courseName: course.name,
      teacherId: course.teacherId ?? "",
      teacherName: course.teacherName ?? "",
      courseHours: Number(totalsByCourse.get(String(course.id))?.courseHours ?? 0),
      generalHours: Number(totalsByCourse.get(String(course.id))?.generalHours ?? 0)
    }));
    const teachers = Array.from(new Map(courses.filter((course) => course.teacherId).map((course) => [course.teacherId, {
      teacherId: course.teacherId,
      teacherName: course.teacherName
    }])).values());
    return {
      courseHours,
      generalHours,
      totalCompleted,
      totalRequired: 20,
      totalRemaining: Math.max(20 - totalCompleted, 0),
      courseRemaining: Math.max(10 - courseHours, 0),
      generalRemaining: Math.max(10 - generalHours, 0),
      completed: courseHours >= 10 && generalHours >= 10,
      pendingCount: Number(totals?.pendingCount ?? 0),
      rule: { total: 20, courseRequired: 10, generalRequired: 10, dailyLimit: 2 },
      teachers,
      courses
    };
  }

  async listSportRecords(
    studentId: string,
    filters: { status?: string; courseId?: string; limit?: number; offset?: number } = {}
  ): Promise<JsonObject[]> {
    const conditions = ["r.student_id = ?"];
    const parameters: any[] = [studentId];
    if (filters.status) {
      conditions.push("r.status = ?");
      parameters.push(filters.status);
    }
    if (filters.courseId) {
      conditions.push("r.course_id = ?");
      parameters.push(filters.courseId);
    }
    const limit = Math.min(Math.max(Math.trunc(filters.limit ?? 500), 1), 500);
    const offset = Math.max(Math.trunc(filters.offset ?? 0), 0);
    parameters.push(limit, offset);
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT r.*, t.title AS task_title
         FROM sport_records r
         LEFT JOIN sport_tasks t ON t.id = r.task_id
        WHERE ${conditions.join(" AND ")} ORDER BY r.submitted_at DESC, r.id DESC LIMIT ? OFFSET ?`,
      parameters
    ));
    const proofMap = await this.proofsByOwners("record", result.map((row) => String(row.id)));
    return result.map((row) => this.recordDto(row, proofMap.get(String(row.id)) ?? []));
  }

  async getSportRecord(studentId: string, recordId: string): Promise<JsonObject | null> {
    const row = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT r.*, t.title AS task_title
         FROM sport_records r
         LEFT JOIN sport_tasks t ON t.id = r.task_id
        WHERE r.id = ? AND r.student_id = ? LIMIT 1`,
      [recordId, studentId]
    ));
    return row ? this.recordDto(row, await this.proofs("record", String(row.id))) : null;
  }

  async createSportRecord(studentId: string, input: CreateRecordInput): Promise<JsonObject> {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      const cached = input.idempotencyKey ? await this.claimIdempotency(connection, studentId, input.idempotencyKey, "record:create", input.idempotencyHash) : null;
      if (cached) {
        await connection.commit();
        return cached;
      }
      await this.assertCourseAccess(connection, studentId, input);
      await this.assertProofOwnership(connection, studentId, input.proofFiles.map((proof) => proof.cosKey));
      const id = randomUUID();
      try {
        await connection.execute(
          `INSERT INTO sport_records
             (id, student_id, course_id, task_id, credit_type, hours, approved_hours,
              description, sport_type, status, ai_review_status, ai_risk_codes,
              submitted_at, submission_date, source)
           VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, '待审核', 'pending', JSON_ARRAY(), CURRENT_TIMESTAMP(3), ?, 'student')`,
          [id, studentId, input.courseId, input.taskId, input.creditType, input.hours, input.description, input.sportType, chineseBusinessDate()]
        );
      } catch (error) {
        if (mysqlCode(error) === "ER_DUP_ENTRY") {
          if (mysqlMessage(error).includes("uq_student_task_submission")) {
            throw new AppError(409, "TASK_ALREADY_SUBMITTED", "该课程任务已经提交过，请使用补材料入口");
          }
          throw new AppError(409, "DAILY_LIMIT", "今日已打卡，每天只能提交一次");
        }
        throw error;
      }
      await this.attachProofs(connection, studentId, input.proofFiles.map((proof) => proof.cosKey), "record", id);
      const response = { id, status: "待审核", submittedAt: new Date().toISOString() };
      if (input.idempotencyKey) await this.saveIdempotentResponse(connection, studentId, input.idempotencyKey, "record:create", input.idempotencyHash, response);
      await connection.commit();
      return response;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  async supplementSportRecord(studentId: string, recordId: string, input: SupplementRecordInput): Promise<JsonObject> {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      const record = first(await connection.execute<RowDataPacket[]>(
        "SELECT id, status FROM sport_records WHERE id = ? AND student_id = ? FOR UPDATE",
        [recordId, studentId]
      ));
      if (!record) throw new AppError(404, "RECORD_NOT_FOUND", "打卡记录不存在");
      const cached = input.idempotencyKey ? await this.claimIdempotency(connection, studentId, input.idempotencyKey, `record:supplement:${recordId}`, input.idempotencyHash) : null;
      if (cached) {
        await connection.commit();
        return cached;
      }
      if (!["补材料", "已驳回"].includes(String(record.status))) throw new AppError(409, "SUPPLEMENT_NOT_ALLOWED", "当前记录状态不允许补交");
      await this.assertProofOwnership(connection, studentId, input.proofFiles.map((proof) => proof.cosKey));
      await connection.execute(
        `INSERT INTO record_supplements (id, record_id, student_id, hours, description, proof_keys, created_at)
         VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))`,
        [randomUUID(), recordId, studentId, input.hours, input.description, JSON.stringify(input.proofFiles.map((proof) => proof.cosKey))]
      );
      await connection.execute(
        `UPDATE sport_records
            SET hours = ?, description = ?, status = '待审核', ai_review_status = 'pending',
                ai_risk_level = NULL, ai_risk_codes = JSON_ARRAY(), ai_review_message = NULL,
                ai_confidence = NULL, ai_reviewed_at = NULL, submitted_at = CURRENT_TIMESTAMP(3)
          WHERE id = ?`,
        [input.hours, input.description, recordId]
      );
      await this.attachProofs(connection, studentId, input.proofFiles.map((proof) => proof.cosKey), "record", recordId);
      const response = { id: recordId, status: "待审核", message: "补充材料已提交" };
      if (input.idempotencyKey) await this.saveIdempotentResponse(connection, studentId, input.idempotencyKey, `record:supplement:${recordId}`, input.idempotencyHash, response);
      await connection.commit();
      return response;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  async listIdentities(studentId: string): Promise<JsonObject[]> {
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      "SELECT * FROM memberships WHERE student_id = ? ORDER BY updated_at DESC",
      [studentId]
    ));
    return result.map((row) => ({
      id: row.id,
      type: row.type,
      organization: row.organization,
      studentId: row.student_id,
      studentName: row.student_name ?? "",
      status: row.status,
      validUntil: iso(row.valid_until),
      offset: row.offset,
      comment: row.comment,
      updatedBy: row.updated_by,
      updatedAt: iso(row.updated_at)
    }));
  }

  async listNotifications(studentId: string): Promise<JsonObject[]> {
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      "SELECT * FROM notifications WHERE student_id = ? ORDER BY created_at DESC LIMIT 200",
      [studentId]
    ));
    return result.map((row) => ({
      id: row.id,
      title: row.title,
      message: row.message,
      time: iso(row.created_at) ?? "",
      category: row.category,
      isUnread: !Boolean(row.is_read),
      targetType: row.target_type,
      targetId: row.target_id
    }));
  }

  async markNotificationRead(studentId: string, notificationId: string): Promise<JsonObject | null> {
    const [result] = await this.pool.execute<ResultSetHeader>(
      "UPDATE notifications SET is_read = 1, read_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND student_id = ?",
      [notificationId, studentId]
    );
    return result.affectedRows ? { id: notificationId, read: true } : null;
  }

  async listCourses(studentId: string, scope: "all" | "current" | "history", semesterId?: string): Promise<JsonObject> {
    const result = await this.courseRows(studentId, scope, semesterId);
    return {
      courses: result.map((row) => ({
        id: row.id,
        code: row.code,
        section: row.section,
        name: row.name,
        teacherId: row.teacherId ?? "",
        teacherName: row.teacherName ?? "",
        status: row.courseStatus,
        enrollmentStatus: row.enrollmentStatus,
        isCurrent: row.semesterStatus === "current",
        semester: {
          id: row.semesterId,
          name: row.semesterName,
          academicYear: row.academicYear,
          term: row.term,
          startDate: row.startDate,
          endDate: row.endDate,
          status: row.semesterStatus
        }
      })),
      scope
    };
  }

  async listTasks(studentId: string): Promise<JsonObject> {
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT t.*, c.code AS course_code, c.section AS course_section, c.name AS course_name,
              r.submitted_at AS completed_at
         FROM sport_tasks t
         JOIN courses c ON c.id = t.course_id
         JOIN course_enrollments e ON e.course_id = c.id AND e.student_id = ? AND e.status = 'enrolled'
         LEFT JOIN (
           SELECT task_id, student_id, MAX(submitted_at) AS completed_at
            FROM sport_records
            WHERE task_id IS NOT NULL
              AND status IN ('待审核', '已通过', '系统抵扣')
            GROUP BY task_id, student_id
         ) r ON r.task_id = t.id AND r.student_id = ?
        ORDER BY t.deadline ASC`,
      [studentId, studentId]
    ));
    const mapped = result.map((row) => ({
      id: row.id,
      courseId: row.course_id,
      courseCode: row.course_code,
      courseSection: row.course_section,
      courseName: row.course_name,
      title: row.title,
      description: row.description ?? "",
      creditType: row.credit_type,
      requiredHours: Number(row.required_hours),
      deadline: iso(row.deadline) ?? "",
      status: row.status,
      completedAt: iso(row.completed_at)
    }));
    return {
      pending: mapped.filter((task) => !task.completedAt),
      completed: mapped.filter((task) => Boolean(task.completedAt))
    };
  }

  async getGrades(studentId: string): Promise<JsonObject> {
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT g.*, u.name AS student_name, c.code AS course_code, c.name AS course_name,
              COALESCE(s.course_hours, 0) AS course_hours, COALESCE(s.general_hours, 0) AS general_hours
         FROM grades g
         JOIN users u ON u.id = g.student_id
         LEFT JOIN courses c ON c.id = g.course_id
         LEFT JOIN (
           SELECT student_id, course_id,
                  SUM(CASE WHEN credit_type='课程相关' AND status='已通过' THEN approved_hours ELSE 0 END) AS course_hours,
                  SUM(CASE WHEN credit_type='其他运动' AND status='已通过' THEN approved_hours ELSE 0 END) AS general_hours
             FROM sport_records GROUP BY student_id, course_id
         ) s ON s.student_id = g.student_id AND (s.course_id = g.course_id OR (s.course_id IS NULL AND g.course_id IS NULL))
        WHERE g.student_id = ? ORDER BY g.updated_at DESC`,
      [studentId]
    ));
    const grades = result.map((row) => ({
      studentId,
      studentName: row.student_name,
      courseId: row.course_id ?? "",
      courseCode: row.course_code ?? "",
      courseName: row.course_name ?? "",
      courseHours: Number(row.course_hours),
      generalHours: Number(row.general_hours),
      checkin: Number(row.checkin_score),
      checkinScore: Number(row.checkin_score),
      exam: Number(row.exam_score),
      attendance: Number(row.attendance_score),
      physical: Number(row.physical_score),
      overallTotal: Number(row.total_score),
      total: Number(row.total_score),
      sourceTrace: row.source_trace ?? "API: /student/grades"
    }));
    const count = grades.length || 1;
    const average = (field: "checkinScore" | "exam" | "attendance" | "physical" | "total") => Math.round(grades.reduce((sum, grade) => sum + Number(grade[field] ?? 0), 0) / count);
    return {
      grades,
      summary: {
        overallCheckinScore: average("checkinScore"),
        overallExam: average("exam"),
        overallAttendance: average("attendance"),
        overallPhysical: average("physical"),
        overallTotal: average("total"),
        totalPossible: 100
      }
    };
  }

  async listExemptions(
    studentId: string,
    category?: "physical_test" | "checkin",
    page: { limit?: number; offset?: number } = {}
  ): Promise<JsonObject[]> {
    const limit = Math.min(Math.max(Math.trunc(page.limit ?? 500), 1), 500);
    const offset = Math.max(Math.trunc(page.offset ?? 0), 0);
    const result = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT e.*, u.name AS student_name
         FROM exemptions e JOIN users u ON u.id = e.student_id
        WHERE e.student_id = ? ${category ? "AND e.category = ?" : ""}
        ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?`,
      category ? [studentId, category, limit, offset] : [studentId, limit, offset]
    ));
    const proofMap = await this.proofsByOwners("exemption", result.map((row) => String(row.id)));
    return result.map((row) => ({
      id: row.id,
      studentId: row.student_id,
      studentName: row.student_name,
      type: row.type,
      category: row.category,
      organization: row.organization,
      reason: row.reason,
      status: row.status,
      proofFiles: proofMap.get(String(row.id)) ?? [],
      reviewComment: row.review_comment,
      reviewerId: row.reviewer_id,
      reviewerName: row.reviewer_name,
      createdAt: iso(row.created_at) ?? "",
      updatedAt: iso(row.updated_at)
    }));
  }

  async createExemption(studentId: string, category: "physical_test" | "checkin", input: CreateExemptionInput): Promise<JsonObject> {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      const scope = `exemption:${category}`;
      const cached = input.idempotencyKey ? await this.claimIdempotency(connection, studentId, input.idempotencyKey, scope, input.idempotencyHash) : null;
      if (cached) {
        await connection.commit();
        return cached;
      }
      await this.assertProofOwnership(connection, studentId, input.proofFiles);
      const id = randomUUID();
      await connection.execute(
        `INSERT INTO exemptions
           (id, student_id, type, category, organization, reason, status, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, 'pending', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))`,
        [id, studentId, input.type, category, input.organization, input.reason]
      );
      await this.attachProofs(connection, studentId, input.proofFiles, "exemption", id);
      const response = { id, status: "pending", createdAt: new Date().toISOString() };
      if (input.idempotencyKey) await this.saveIdempotentResponse(connection, studentId, input.idempotencyKey, scope, input.idempotencyHash, response);
      await connection.commit();
      return response;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  async supplementExemption(
    studentId: string,
    exemptionId: string,
    category: "physical_test" | "checkin",
    input: SupplementExemptionInput
  ): Promise<JsonObject> {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      const exemption = first(await connection.execute<RowDataPacket[]>(
        `SELECT id, status, type FROM exemptions
          WHERE id = ? AND student_id = ? AND category = ? FOR UPDATE`,
        [exemptionId, studentId, category]
      ));
      if (!exemption) throw new AppError(404, "EXEMPTION_NOT_FOUND", "申请不存在");
      const scope = `exemption:supplement:${exemptionId}`;
      const cached = input.idempotencyKey ? await this.claimIdempotency(connection, studentId, input.idempotencyKey, scope, input.idempotencyHash) : null;
      if (cached) {
        await connection.commit();
        return cached;
      }
      if (!["supplement_required", "rejected"].includes(String(exemption.status))) {
        throw new AppError(409, "EXEMPTION_SUPPLEMENT_NOT_ALLOWED", "当前申请状态不允许补交材料");
      }
      await this.assertProofOwnership(connection, studentId, input.proofFiles);
      const supplementId = randomUUID();
      await connection.execute(
        `INSERT INTO exemption_supplements
           (id, exemption_id, student_id, reason, organization, proof_keys, created_at)
         VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))`,
        [supplementId, exemptionId, studentId, input.reason, input.organization, JSON.stringify(input.proofFiles)]
      );
      await connection.execute(
        `UPDATE exemptions
            SET reason = ?, organization = COALESCE(?, organization), status = 'reviewing',
                review_comment = NULL, updated_at = CURRENT_TIMESTAMP(3)
          WHERE id = ?`,
        [input.reason, input.organization, exemptionId]
      );
      await this.attachProofs(connection, studentId, input.proofFiles, "exemption", exemptionId);
      const response = { id: exemptionId, status: "reviewing", createdAt: new Date().toISOString() };
      if (input.idempotencyKey) await this.saveIdempotentResponse(connection, studentId, input.idempotencyKey, scope, input.idempotencyHash, response);
      await connection.commit();
      return response;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  async convertEndurance(timeSeconds: number, gender: string, gradeLevel: string): Promise<JsonObject | null> {
    const gradeGroup = ["freshman", "sophomore"].includes(gradeLevel) ? "freshman_sophomore" : "junior_senior";
    const runType = gender === "male" ? "1000m" : "800m";
    const rule = first(await this.pool.execute<RowDataPacket[]>(
      `SELECT * FROM endurance_scoring_rules
        WHERE gender = ? AND grade_group = ? AND run_type = ?
          AND ? BETWEEN min_seconds AND max_seconds
        ORDER BY score DESC LIMIT 1`,
      [gender, gradeGroup, runType, timeSeconds]
    ));
    if (!rule) return null;
    return {
      score: Number(rule.score),
      tier: rule.tier,
      timeSeconds,
      gender,
      gradeLevel,
      gradeGroup,
      range: { min: Number(rule.min_seconds), max: Number(rule.max_seconds) },
      note: rule.note
    };
  }

  async registerProofFiles(studentId: string, proofs: ProofFile[]): Promise<void> {
    const connection = await this.pool.getConnection();
    try {
      await connection.beginTransaction();
      for (const proof of proofs) {
        await connection.execute(
          `INSERT INTO proof_files
             (id, student_id, cos_key, url, media_type, mime_type, size_bytes, owner_type, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, 'upload', CURRENT_TIMESTAMP(3))`,
          [randomUUID(), studentId, proof.cosKey, proof.url, proof.mediaType, proof.mimeType, proof.size]
        );
      }
      await connection.commit();
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  }

  private async requireUser(id: string): Promise<UserRecord> {
    const user = await this.findUserById(id);
    if (!user) throw new AppError(404, "USER_NOT_FOUND", "用户不存在");
    return user;
  }

  private async courseRows(studentId: string, scope: "all" | "current" | "history", semesterId?: string): Promise<RowDataPacket[]> {
    const conditions = ["e.student_id = ?"];
    const parameters: any[] = [studentId];
    if (scope === "current") conditions.push("s.status = 'current'");
    if (scope === "history") conditions.push("s.status <> 'current'");
    if (semesterId) {
      conditions.push("s.id = ?");
      parameters.push(semesterId);
    }
    return rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT c.id, c.code, c.section, c.name, c.teacher_id AS teacherId,
              teacher.name AS teacherName, c.status AS courseStatus,
              e.status AS enrollmentStatus, s.id AS semesterId, s.name AS semesterName,
              s.academic_year AS academicYear, s.term, s.start_date AS startDate,
              s.end_date AS endDate, s.status AS semesterStatus
         FROM course_enrollments e
         JOIN courses c ON c.id = e.course_id
         JOIN semesters s ON s.id = c.semester_id
         LEFT JOIN users teacher ON teacher.id = c.teacher_id
        WHERE ${conditions.join(" AND ")}
        ORDER BY s.start_date DESC, c.code, c.section`,
      parameters
    ));
  }

  private recordDto(row: RowDataPacket, proofFiles: ProofFile[]): JsonObject {
    return {
      id: row.id,
      courseId: row.course_id,
      taskId: row.task_id,
      taskTitle: row.task_title,
      creditType: row.credit_type,
      hours: Number(row.hours),
      approvedHours: Number(row.approved_hours),
      description: row.description,
      proofFiles,
      sportType: row.sport_type,
      aiReviewStatus: row.ai_review_status,
      aiRiskLevel: row.ai_risk_level,
      aiRiskCodes: jsonArray(row.ai_risk_codes),
      aiReviewMessage: row.ai_review_message,
      aiConfidence: row.ai_confidence == null ? null : Number(row.ai_confidence),
      aiReviewedAt: iso(row.ai_reviewed_at),
      status: row.status,
      reviewComment: row.review_comment,
      submittedAt: iso(row.submitted_at),
      reviewedAt: iso(row.reviewed_at)
    };
  }

  private async proofs(ownerType: "record" | "exemption", ownerId: string): Promise<ProofFile[]> {
    return (await this.proofsByOwners(ownerType, [ownerId])).get(ownerId) ?? [];
  }

  private async proofsByOwners(
    ownerType: "record" | "exemption",
    ownerIds: string[]
  ): Promise<Map<string, ProofFile[]>> {
    const uniqueOwnerIds = [...new Set(ownerIds)];
    const result = new Map<string, ProofFile[]>(uniqueOwnerIds.map((id) => [id, []]));
    if (uniqueOwnerIds.length === 0) return result;
    const placeholders = uniqueOwnerIds.map(() => "?").join(",");
    const proofRows = rows(await this.pool.execute<RowDataPacket[]>(
      `SELECT owner_id, url, cos_key, media_type, mime_type, size_bytes
         FROM proof_files
        WHERE owner_type = ? AND owner_id IN (${placeholders})
        ORDER BY owner_id, created_at`,
      [ownerType, ...uniqueOwnerIds]
    ));
    for (const row of proofRows) {
      const ownerId = String(row.owner_id);
      result.get(ownerId)?.push({
        url: row.url,
        cosKey: row.cos_key,
        mediaType: row.media_type,
        mimeType: row.mime_type,
        size: Number(row.size_bytes)
      });
    }
    return result;
  }

  private async assertCourseAccess(
    connection: Queryable,
    studentId: string,
    input: CreateRecordInput
  ): Promise<void> {
    if (input.creditType !== "课程相关") return;
    if (!input.courseId) throw new AppError(422, "COURSE_REQUIRED", "课程相关打卡必须指定课程");
    const enrollment = first(await connection.execute<RowDataPacket[]>(
      `SELECT course_id FROM course_enrollments
        WHERE student_id = ? AND course_id = ? AND status = 'enrolled' LIMIT 1`,
      [studentId, input.courseId]
    ));
    if (!enrollment) throw new AppError(422, "COURSE_NOT_ENROLLED", "不能向未选课程提交打卡");
    if (!input.taskId) return;
    const task = first(await connection.execute<RowDataPacket[]>(
      "SELECT id, status, deadline FROM sport_tasks WHERE id = ? AND course_id = ? LIMIT 1",
      [input.taskId, input.courseId]
    ));
    if (!task) throw new AppError(422, "TASK_COURSE_MISMATCH", "课程任务与所选课程不匹配");
    if (String(task.status) !== "进行中") throw new AppError(409, "TASK_CLOSED", "该课程任务当前不可提交");
    const deadline = iso(task.deadline);
    if (deadline && Date.parse(deadline) < Date.now()) throw new AppError(409, "TASK_EXPIRED", "该课程任务已截止");
    const existing = first(await connection.execute<RowDataPacket[]>(
      "SELECT id FROM sport_records WHERE student_id = ? AND task_id = ? LIMIT 1",
      [studentId, input.taskId]
    ));
    if (existing) throw new AppError(409, "TASK_ALREADY_SUBMITTED", "该课程任务已经提交过，请使用补材料入口");
  }

  private async assertProofOwnership(connection: Queryable, studentId: string, keys: string[]): Promise<void> {
    if (keys.length === 0) throw new AppError(422, "PROOF_REQUIRED", "至少需要一个凭证");
    const uniqueKeys = [...new Set(keys)];
    if (uniqueKeys.length !== keys.length) {
      throw new AppError(422, "DUPLICATE_PROOF_REFERENCE", "同一凭证不能重复提交");
    }
    const placeholders = uniqueKeys.map(() => "?").join(",");
    const result = rows(await connection.execute<RowDataPacket[]>(
      `SELECT cos_key FROM proof_files
        WHERE student_id = ? AND owner_type = 'upload' AND cos_key IN (${placeholders})
        FOR UPDATE`,
      [studentId, ...uniqueKeys]
    ));
    if (result.length !== uniqueKeys.length) {
      throw new AppError(422, "INVALID_PROOF_REFERENCE", "凭证不存在、已使用或不属于当前学生");
    }
  }

  private async attachProofs(connection: Queryable, studentId: string, keys: string[], ownerType: "record" | "exemption", ownerId: string): Promise<void> {
    const placeholders = keys.map(() => "?").join(",");
    const [result] = await connection.execute<ResultSetHeader>(
      `UPDATE proof_files SET owner_type = ?, owner_id = ?
        WHERE student_id = ? AND owner_type = 'upload' AND cos_key IN (${placeholders})`,
      [ownerType, ownerId, studentId, ...keys]
    );
    if (result.affectedRows !== keys.length) {
      throw new AppError(409, "PROOF_CLAIM_CONFLICT", "凭证已被其他请求使用，请重新上传");
    }
  }

  private async claimIdempotency(connection: Queryable, studentId: string, key: string, scope: string, requestHash: string | undefined): Promise<JsonObject | null> {
    const hash = requiredIdempotencyHash(requestHash);
    try {
      await connection.execute(
        `INSERT INTO idempotency_keys (student_id, idempotency_key, scope, request_hash, response_json, created_at)
         VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))`,
        [studentId, key, scope, hash, JSON.stringify({ __idempotencyPending: true })]
      );
      return null;
    } catch (error) {
      if (mysqlCode(error) !== "ER_DUP_ENTRY") throw error;
    }
    const result = first(await connection.execute<RowDataPacket[]>(
      `SELECT request_hash, response_json FROM idempotency_keys
        WHERE student_id = ? AND idempotency_key = ? AND scope = ?
        LIMIT 1 FOR UPDATE`,
      [studentId, key, scope]
    ));
    if (!result) throw new AppError(409, "IDEMPOTENCY_CONFLICT", "幂等请求正在处理中，请稍后重试");
    if (String(result.request_hash) !== hash) {
      throw new AppError(409, "IDEMPOTENCY_KEY_REUSED", "同一个 Idempotency-Key 不能用于不同的请求内容");
    }
    const response = typeof result.response_json === "string"
      ? JSON.parse(result.response_json) as JsonObject
      : result.response_json as JsonObject;
    if (response.__idempotencyPending === true) {
      throw new AppError(409, "IDEMPOTENCY_CONFLICT", "幂等请求正在处理中，请稍后重试");
    }
    return response;
  }

  private async saveIdempotentResponse(connection: Queryable, studentId: string, key: string, scope: string, requestHash: string | undefined, response: JsonObject): Promise<void> {
    const hash = requiredIdempotencyHash(requestHash);
    const [result] = await connection.execute<ResultSetHeader>(
      `UPDATE idempotency_keys SET response_json = ?
        WHERE student_id = ? AND idempotency_key = ? AND scope = ? AND request_hash = ?`,
      [JSON.stringify(response), studentId, key, scope, hash]
    );
    if (result.affectedRows !== 1) {
      throw new AppError(500, "IDEMPOTENCY_STATE_LOST", "幂等请求状态保存失败");
    }
  }
}
