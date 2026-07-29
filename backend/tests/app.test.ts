import { createHash, pbkdf2Sync } from "node:crypto";
import bcrypt from "bcryptjs";
import request from "supertest";
import { beforeAll, describe, expect, it } from "vitest";
import { createApp } from "../src/app";
import { signAccessToken } from "../src/auth";
import { ConcurrencyGate } from "../src/concurrency";
import { loadConfig } from "../src/config/env";
import { createdTableNames, untrackedTableConflicts } from "../src/db/migration-safety";
import { AppError } from "../src/errors";
import { idempotencyRequestHash } from "../src/idempotency";
import { DUMMY_PASSWORD_HASH, verifyPassword } from "../src/password";
import type { BackendStore, JsonObject } from "../src/store";
import type { ObjectStorage } from "../src/storage/storage";
import type { ContactBindingStatus, ContactChannel, CreateExemptionInput, CreateFeedbackInput, CreateRecordInput, ProofFile, PushDeviceRegistration, SupplementExemptionInput, UploadedFileInput, UserRecord } from "../src/types";

const studentId = "10000000-0000-4000-8000-000000000001";
let passwordHash = "";

beforeAll(async () => {
  passwordHash = await bcrypt.hash("correct-password", 4);
});

class FakeStore implements BackendStore {
  exemptionSupplementCalls = 0;
  proofRegistrations: ProofFile[][] = [];
  lastRecordFilters: { courseId?: string; limit?: number; offset?: number } | undefined;
  lastExemptionPage: { limit?: number; offset?: number } | undefined;
  feedbackTickets: JsonObject[] = [];
  user(): UserRecord {
    return {
      id: studentId,
      studentNumber: "20260001",
      email: "student@example.invalid",
      passwordHash,
      role: "student",
      name: "测试学生",
      college: "测试学院",
      className: "一班",
      gender: "male",
      preferredLanguage: "zh-CN",
      gradeLevel: "freshman",
      admissionYear: 2026,
      status: "正常",
      tokenVersion: 0
    };
  }
  admin(): UserRecord {
    return {
      ...this.user(),
      id: "10000000-0000-4000-8000-000000000099",
      studentNumber: "admin",
      email: "admin@example.invalid",
      role: "admin"
    };
  }
  async ping() { return true; }
  async close() {}
  async findUserByAccount(account: string) { return ["20260001", "student@example.invalid"].includes(account) ? this.user() : null; }
  async findUserById(id: string) {
    if (id === studentId) return this.user();
    return id === this.admin().id ? this.admin() : null;
  }
  contactBindingStatus: ContactBindingStatus = {
    accountStatus: "ACTIVE",
    contacts: {
      email: { masked: null, verified: false },
      phone: { masked: null, verified: false }
    }
  };
  private contactCodes = new Map<string, string>();
  async getContactBindingStatus() { return this.contactBindingStatus; }
  async issueContactVerificationCode(_id: string, channel: ContactChannel, contact: string, codeHash: string) {
    this.contactCodes.set(`${channel}:${contact}`, codeHash);
  }
  async verifyContactVerificationCode(_id: string, channel: ContactChannel, contact: string, codeHash: string) {
    if (this.contactCodes.get(`${channel}:${contact}`) !== codeHash) {
      throw new AppError(422, "CONTACT_CODE_INVALID", "验证码无效或已过期，请重新获取");
    }
    this.contactBindingStatus = {
      accountStatus: "ACTIVE",
      contacts: {
        ...this.contactBindingStatus.contacts,
        [channel]: { masked: channel === "email" ? "t***@example.invalid" : "+86138****0000", verified: true }
      }
    };
    return { ...(await this.getProfile()), account_status: "ACTIVE", contacts: this.contactBindingStatus.contacts };
  }
  preferredLanguage: "zh-CN" | "en" = "zh-CN";
  async getProfile() {
    return {
      id: studentId,
      name: "测试学生",
      email: "student@example.invalid",
      role: "student",
      college: "测试学院",
      className: "一班",
      status: "正常",
      preferredLanguage: this.preferredLanguage,
      enrolledCourses: 1
    };
  }
  async updateProfile(_id: string, gender: "male" | "female" | null) { return { ...(await this.getProfile()), gender }; }
  async updateLanguagePreference(_id: string, language: "zh-CN" | "en") { this.preferredLanguage = language; return { language }; }
  async getSportSummary() { return { courseHours: 1, generalHours: 2, totalCompleted: 3, totalRequired: 20, totalRemaining: 17, courseRemaining: 9, generalRemaining: 8, completed: false, rule: { total: 20, courseRequired: 10, generalRequired: 10, dailyLimit: 2 }, teachers: [], courses: [] }; }
  async listSportRecords(_id: string, filters?: { courseId?: string; limit?: number; offset?: number }) { this.lastRecordFilters = filters; return []; }
  async getSportRecord(_studentId: string, recordId: string) { return recordId === "record-1" ? { id: recordId, creditType: "课程相关", hours: 1, proofFiles: [{ url: "https://expired.invalid/proof.png", cosKey: "proofs/record-1.png", mediaType: "image", mimeType: "image/png", size: 40 }], aiRiskCodes: [] } : null; }
  async createSportRecord(_id: string, input: CreateRecordInput) {
    return {
      id: "record-1",
      status: "待审核",
      submittedAt: new Date().toISOString(),
      received: input.creditType,
      startTime: input.startTime,
      endTime: input.endTime,
      actualDurationSeconds: input.actualDurationSeconds
    };
  }
  async listIdentities() { return []; }
  async listNotifications() { return []; }
  async markNotificationRead(_id: string, notificationId: string) { return notificationId === "notice-1" ? { id: notificationId, read: true } : null; }
  pushDeviceRegistrations: Array<{ studentId: string; input: PushDeviceRegistration }> = [];
  pushDeviceRemovals: Array<{ studentId: string; token: string }> = [];
  async registerPushDevice(studentId: string, input: PushDeviceRegistration) { this.pushDeviceRegistrations.push({ studentId, input }); }
  async unregisterPushDevice(studentId: string, token: string) { this.pushDeviceRemovals.push({ studentId, token }); }
  async listCourses(_id: string, scope: "all" | "current" | "history") { return { courses: [], scope }; }
  async getCheckInTimeWindow() {
    return {
      windowMode: "semester_wide", dateRangeStart: null, dateRangeEnd: null,
      dailyStartTime: "06:00", dailyEndTime: "22:00", excludedDates: [], semesterDeadline: null
    };
  }
  async listTasks() { return { pending: [], completed: [] }; }
  async getGrades() { return { grades: [], summary: { overallCheckinScore: 0, overallExam: 0, overallAttendance: 0, overallPhysical: 0, overallTotal: 0, totalPossible: 100 } }; }
  async listExemptions(_id: string, _category?: "physical_test" | "checkin", page?: { limit?: number; offset?: number }) { this.lastExemptionPage = page; return []; }
  async createExemption(_id: string, _category: "physical_test" | "checkin", _input: CreateExemptionInput) { return { id: "exemption-1", status: "pending", createdAt: new Date().toISOString() }; }
  async supplementExemption(_id: string, exemptionId: string, _category: "physical_test" | "checkin", _input: SupplementExemptionInput) { this.exemptionSupplementCalls += 1; return { id: exemptionId, status: "reviewing", createdAt: new Date().toISOString() }; }
  async createFeedback(_id: string, input: CreateFeedbackInput) {
    const ticket = { id: `feedback-${this.feedbackTickets.length + 1}`, ticketNumber: `FB-TEST${this.feedbackTickets.length + 1}`, ...input, status: "pending", createdAt: new Date().toISOString(), updatedAt: null, reply: null };
    this.feedbackTickets.unshift(ticket);
    return ticket;
  }
  async listFeedbackTickets() { return this.feedbackTickets; }
  async convertEndurance(time: number, gender: string, grade: string): Promise<JsonObject | null> { return time > 600 ? null : { score: 80, tier: "pass", timeSeconds: time, gender, gradeLevel: grade, gradeGroup: "freshman_sophomore", range: { min: 271, max: 330 } }; }
  helpArticles: JsonObject[] = [{ id: "help-1", title: "Published help", category: "Getting started", content: "Server-managed content", sortOrder: 10, status: "published" }];
  async listPublishedHelpArticles() { return this.helpArticles.filter((article) => article.status === "published"); }
  async listHelpArticles() { return this.helpArticles; }
  async upsertHelpArticle(adminId: string, articleId: string | null, input: { title: string; category: string; content: string; sortOrder: number; status: "draft" | "published" | "offline" }) {
    const id = articleId ?? `help-${this.helpArticles.length + 1}`;
    const article = { id, ...input, updatedBy: adminId };
    const index = this.helpArticles.findIndex((item) => item.id === id);
    if (index >= 0) this.helpArticles[index] = article;
    else this.helpArticles.push(article);
    return article;
  }
  async registerProofFiles(_id: string, proofs: ProofFile[]) { this.proofRegistrations.push(proofs); }
}

class FakeStorage implements ObjectStorage {
  readonly deletedKeys: string[] = [];
  readonly putKeys: string[] = [];
  async health() { return true; }
  async put(student: string, file: UploadedFileInput, cosKey = `proofs/${student}/proof.png`): Promise<ProofFile> { this.putKeys.push(cosKey); return { url: `https://files.invalid/${student}/proof.png`, cosKey, mediaType: file.mediaType, mimeType: file.mimeType, size: file.size }; }
  async url(cosKey: string) { return `https://files.invalid/signed/${encodeURIComponent(cosKey)}`; }
  async delete(cosKey: string) { this.deletedKeys.push(cosKey); }
}

class FailingUploadStore extends FakeStore {
  override async registerProofFiles(): Promise<void> {
    throw new Error("database unavailable");
  }
}

class IdempotentFakeStore extends FakeStore {
  private readonly mutations = new Map<string, {
    hash: string | undefined;
    response: Awaited<ReturnType<FakeStore["createSportRecord"]>>;
  }>();

  override async createSportRecord(id: string, input: CreateRecordInput) {
    if (!input.idempotencyKey) return super.createSportRecord(id, input);
    const previous = this.mutations.get(input.idempotencyKey);
    if (previous) {
      if (previous.hash !== input.idempotencyHash) {
        throw new AppError(409, "IDEMPOTENCY_KEY_REUSED", "same key, different request");
      }
      return previous.response;
    }
    const response = await super.createSportRecord(id, input);
    this.mutations.set(input.idempotencyKey, { hash: input.idempotencyHash, response });
    return response;
  }
}

function testConfig(overrides: NodeJS.ProcessEnv = {}) {
  return loadConfig({
    NODE_ENV: "test",
    DATABASE_URL: "mysql://user:pass@localhost:3306/test",
    JWT_SECRET: "test-secret-with-at-least-thirty-two-characters",
    PUBLIC_BASE_URL: "http://localhost:3005",
    STORAGE_DRIVER: "local",
    CORS_ORIGINS: "http://localhost:3000",
    LOG_LEVEL: "silent",
    ...overrides
  });
}

async function authenticatedAgent(store = new FakeStore()) {
  const storage = new FakeStorage();
  const app = createApp({ config: testConfig(), store, storage });
  const login = await request(app).post("/api/auth/login").send({ account: "20260001", password: "correct-password", role: "student", clientType: "mobile" });
  expect(login.status).toBe(200);
  return { app, store, storage, token: login.body.token as string };
}

describe("BNBU API", () => {
  it("reports dependency health and security headers", async () => {
    const app = createApp({ config: testConfig(), store: new FakeStore(), storage: new FakeStorage() });
    const response = await request(app).get("/api/health");
    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ ok: true, db: true, cos: true });
    expect(response.headers["x-request-id"]).toBeTruthy();
    expect(response.headers["x-powered-by"]).toBeUndefined();
    expect(response.headers["x-content-type-options"]).toBe("nosniff");
  });

  it("returns unified JSON for denied CORS origins", async () => {
    const app = createApp({ config: testConfig(), store: new FakeStore(), storage: new FakeStorage() });
    const response = await request(app).get("/api/health").set("Origin", "https://evil.example");
    expect(response.status).toBe(403);
    expect(response.body).toMatchObject({ code: "CORS_ORIGIN_DENIED" });
    expect(response.body.requestId).toBeTruthy();
  });

  it("returns unified JSON for oversized request bodies", async () => {
    const app = createApp({ config: testConfig(), store: new FakeStore(), storage: new FakeStorage() });
    const response = await request(app)
      .post("/api/auth/login")
      .set("Content-Type", "application/json")
      .send(JSON.stringify({ payload: "x".repeat(1_100_000) }));
    expect(response.status).toBe(413);
    expect(response.body.code).toBe("REQUEST_TOO_LARGE");
    expect(response.body.requestId).toBeTruthy();
  });

  it("rate-limits login attempts by normalized account with the unified error shape", async () => {
    const app = createApp({
      config: testConfig({ LOGIN_RATE_LIMIT_PER_15_MIN: "1" }),
      store: new FakeStore(),
      storage: new FakeStorage()
    });
    const body = { account: "20260001", password: "wrong-password", role: "student", clientType: "mobile" };
    expect((await request(app).post("/api/auth/login").send(body)).status).toBe(401);
    const limited = await request(app).post("/api/auth/login").send({ ...body, account: " 20260001 " });
    expect(limited.status).toBe(429);
    expect(limited.body.code).toBe("LOGIN_RATE_LIMITED");
    expect(limited.body.requestId).toBeTruthy();
  });

  it("exempts health checks from the global rate limiter while preserving it for API traffic", async () => {
    const app = createApp({
      config: testConfig({ GLOBAL_RATE_LIMIT_PER_15_MIN: "1" }),
      store: new FakeStore(),
      storage: new FakeStorage()
    });

    expect((await request(app).get("/api/health")).status).toBe(200);
    expect((await request(app).get("/api/health")).status).toBe(200);

    // This request consumes the sole global IP quota; health probes must still work afterwards.
    expect((await request(app).get("/api/sport/summary")).status).toBe(401);
    expect((await request(app).get("/api/health")).status).toBe(200);

    const limited = await request(app).get("/api/sport/summary");
    expect(limited.status).toBe(429);
    expect(limited.body.code).toBe("RATE_LIMITED");
    expect(limited.body.requestId).toBeTruthy();
  });

  it("returns the Android-compatible auth error shape", async () => {
    const app = createApp({ config: testConfig(), store: new FakeStore(), storage: new FakeStorage() });
    const response = await request(app).get("/api/sport/summary");
    expect(response.status).toBe(401);
    expect(response.body).toMatchObject({ code: "AUTH_REQUIRED", message: "未登录" });
    expect(response.body.requestId).toBeTruthy();
  });

  it("logs in and returns raw profile/summary DTOs", async () => {
    const { app, token } = await authenticatedAgent();
    const profile = await request(app).get("/api/student/profile").set("Authorization", `Bearer ${token}`);
    expect(profile.status).toBe(200);
    expect(profile.body.id).toBe(studentId);
    expect(profile.body.className).toBe("一班");
    expect(profile.body.data).toBeUndefined();
    const summary = await request(app).get("/api/sport/summary").set("Authorization", `Bearer ${token}`);
    expect(summary.body.courseHours).toBeTypeOf("number");
  });

  it("requires a verified contact after course enrollment while preserving the activation endpoints", async () => {
    const store = new FakeStore();
    store.contactBindingStatus = {
      accountStatus: "PENDING_CONTACT_BINDING",
      contacts: {
        email: { masked: null, verified: false },
        phone: { masked: null, verified: false }
      }
    };
    const { app, token } = await authenticatedAgent(store);
    const auth = { Authorization: `Bearer ${token}` };

    const profile = await request(app).get("/api/student/profile").set(auth);
    expect(profile.status).toBe(200);

    const blocked = await request(app).get("/api/sport/summary").set(auth);
    expect(blocked.status).toBe(403);
    expect(blocked.body.code).toBe("CONTACT_BINDING_REQUIRED");

    const codeRequest = await request(app)
      .post("/api/v1/student/contacts/email/send-code")
      .set(auth)
      .send({ email: "student.contact@example.invalid" });
    expect(codeRequest.status).toBe(204);
  });

  it("returns the server-owned check-in time window", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app)
      .get("/api/student/checkin-time-window")
      .set("Authorization", `Bearer ${token}`);
    expect(response.status).toBe(200);
    expect(response.headers["cache-control"]).toContain("no-store");
    expect(response.body).toMatchObject({
      windowMode: "semester_wide",
      dailyStartTime: "06:00",
      dailyEndTime: "22:00",
      excludedDates: []
    });
  });

  it("returns only published server-managed help articles", async () => {
    const { app, store, token } = await authenticatedAgent();
    store.helpArticles.push({ id: "help-offline", title: "Offline", category: "", content: "Hidden", sortOrder: 20, status: "offline" });
    const response = await request(app)
      .get("/api/common/help-articles")
      .set("Authorization", `Bearer ${token}`);
    expect(response.status).toBe(200);
    expect(response.headers["cache-control"]).toContain("no-store");
    expect(response.body).toEqual([expect.objectContaining({ id: "help-1", title: "Published help" })]);
  });

  it("lets an administrator publish and take help content offline", async () => {
    const store = new FakeStore();
    const config = testConfig();
    const app = createApp({ config, store, storage: new FakeStorage() });
    const adminToken = signAccessToken(config, store.admin());
    const article = {
      title: "Account help",
      category: "Accounts",
      content: "Published by an administrator",
      sortOrder: 20,
      status: "published"
    };
    const created = await request(app).post("/api/admin/help-articles")
      .set("Authorization", `Bearer ${adminToken}`).send(article);
    expect(created.status).toBe(201);

    const offline = await request(app).put(`/api/admin/help-articles/${created.body.id}`)
      .set("Authorization", `Bearer ${adminToken}`).send({ ...article, status: "offline" });
    expect(offline.status).toBe(200);
    expect(offline.body.status).toBe("offline");
    expect((await store.listPublishedHelpArticles()).some((item) => item.id === created.body.id)).toBe(false);
  });

  it("refreshes stored proof URLs before returning record history", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app)
      .get("/api/sport/records/record-1")
      .set("Authorization", `Bearer ${token}`);
    expect(response.status).toBe(200);
    expect(response.body.proofFiles[0].url).toBe(
      "https://files.invalid/signed/proofs%2Frecord-1.png"
    );
  });

  it("validates and forwards list pagination", async () => {
    const { app, token, store } = await authenticatedAgent();
    const records = await request(app)
      .get("/api/sport/records?limit=25&offset=50")
      .set("Authorization", `Bearer ${token}`);
    expect(records.status).toBe(200);
    expect(store.lastRecordFilters).toMatchObject({ limit: 25, offset: 50 });

    const exemptions = await request(app)
      .get("/api/student/checkin-exemptions?limit=10&offset=20")
      .set("Authorization", `Bearer ${token}`);
    expect(exemptions.status).toBe(200);
    expect(store.lastExemptionPage).toEqual({ limit: 10, offset: 20 });

    const invalid = await request(app)
      .get("/api/sport/records?limit=501")
      .set("Authorization", `Bearer ${token}`);
    expect(invalid.status).toBe(422);
  });

  it("rejects general records that smuggle course references", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app)
      .post("/api/sport/records")
      .set("Authorization", `Bearer ${token}`)
      .send({
        creditType: "其他运动",
        courseId: "course-1",
        taskId: null,
        hours: 1,
        description: "run",
        proofFiles: [{ cosKey: "proofs/a.jpg", mediaType: "image", mimeType: "image/jpeg", size: 100 }]
      });
    expect(response.status).toBe(422);
    expect(response.body.code).toBe("VALIDATION_ERROR");
  });

  it("submits a sport record with numeric values", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app)
      .post("/api/sport/records")
      .set("Authorization", `Bearer ${token}`)
      .set("Idempotency-Key", "record-test-0001")
      .send({ creditType: "课程相关", courseId: "course-1", taskId: "task-1", hours: 2, startTime: "2026-07-14T08:00:00.000Z", endTime: "2026-07-14T10:00:00.000Z", actualDurationSeconds: 7200, description: "完成训练", remark: "与同学一起训练", proofFiles: [{ cosKey: "proofs/a.jpg", mediaType: "image", mimeType: "image/jpeg", size: 100 }], sportType: "跑步" });
    expect(response.status).toBe(201);
    expect(response.body).toMatchObject({ id: "record-1", received: "课程相关", startTime: "2026-07-14T08:00:00.000Z", endTime: "2026-07-14T10:00:00.000Z", actualDurationSeconds: 7200 });
  });

  it("rejects reuse of an idempotency key with a different normalized payload", async () => {
    const store = new IdempotentFakeStore();
    const { app, token } = await authenticatedAgent(store);
    const payload = {
      creditType: "璇剧▼鐩稿叧",
      courseId: "course-1",
      taskId: "task-1",
      hours: 1,
      startTime: "2026-07-14T08:00:00.000Z",
      endTime: "2026-07-14T09:00:00.000Z",
      actualDurationSeconds: 3600,
      description: "run",
      proofFiles: [{ cosKey: "proofs/a.jpg", mediaType: "image", mimeType: "image/jpeg", size: 100 }]
    };
    const validPayload = { ...payload, creditType: "course" };
    const first = await request(app).post("/api/sport/records").set("Authorization", `Bearer ${token}`).set("Idempotency-Key", "stable-record-key").send(validPayload);
    expect(first.status).toBe(201);
    const replay = await request(app).post("/api/sport/records").set("Authorization", `Bearer ${token}`).set("Idempotency-Key", "stable-record-key").send(validPayload);
    expect(replay.status).toBe(201);
    const changed = await request(app).post("/api/sport/records").set("Authorization", `Bearer ${token}`).set("Idempotency-Key", "stable-record-key").send({ ...validPayload, hours: 2 });
    expect(changed.status).toBe(409);
    expect(changed.body.code).toBe("IDEMPOTENCY_KEY_REUSED");
  });

  it("rejects organization on physical-test exemptions before database access", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app)
      .post("/api/student/physical-test-exemptions")
      .set("Authorization", `Bearer ${token}`)
      .send({ type: "800m", reason: "medical reason", proofFiles: ["proofs/a.jpg"], organization: "not applicable" });
    expect(response.status).toBe(422);
    expect(response.body.code).toBe("ORGANIZATION_NOT_ALLOWED");
  });

  it("uses the dedicated exemption supplement route", async () => {
    const { app, token, store } = await authenticatedAgent();
    const response = await request(app)
      .post("/api/student/physical-test-exemptions/exemption-1/supplements")
      .set("Authorization", `Bearer ${token}`)
      .set("Idempotency-Key", "exemption-supplement-0001")
      .send({ reason: "补充医院诊断材料", proofFiles: ["proofs/new.jpg"] });
    expect(response.status).toBe(201);
    expect(response.body).toMatchObject({ id: "exemption-1", status: "reviewing" });
    expect(store.exemptionSupplementCalls).toBe(1);
  });

  it("accepts a real PNG signature and rejects disguised media", async () => {
    const { app, token, store, storage } = await authenticatedAgent();
    const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(32)]);
    const accepted = await request(app).post("/api/upload/proof").set("Authorization", `Bearer ${token}`).attach("files", png, { filename: "proof.png", contentType: "image/png" });
    expect(accepted.status).toBe(201);
    expect(accepted.body.count).toBe(1);
    expect(store.proofRegistrations[0]?.[0]?.cosKey).toBe(storage.putKeys[0]);
    const rejected = await request(app).post("/api/upload/proof").set("Authorization", `Bearer ${token}`).attach("files", Buffer.from("not an image"), { filename: "fake.png", contentType: "image/png" });
    expect(rejected.status).toBe(415);
    expect(rejected.body.code).toBe("UNSUPPORTED_MEDIA");
  });

  it("does not create objects when database reservation fails", async () => {
    const store = new FailingUploadStore();
    const storage = new FakeStorage();
    const app = createApp({ config: testConfig(), store, storage });
    const login = await request(app).post("/api/auth/login").send({
      account: "20260001",
      password: "correct-password",
      role: "student",
      clientType: "mobile"
    });
    const png = Buffer.concat([
      Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      Buffer.alloc(32)
    ]);
    const response = await request(app)
      .post("/api/upload/proof")
      .set("Authorization", `Bearer ${login.body.token as string}`)
      .attach("files", png, { filename: "proof.png", contentType: "image/png" });
    expect(response.status).toBe(500);
    expect(storage.deletedKeys).toEqual([]);
    expect(storage.putKeys).toEqual([]);
  });

  it("rejects endurance times outside configured rules", async () => {
    const { app, token } = await authenticatedAgent();
    const response = await request(app).post("/api/scoring/convert-endurance").set("Authorization", `Bearer ${token}`).send({ timeSeconds: 780, gender: "male", gradeLevel: "freshman" });
    expect(response.status).toBe(422);
    expect(response.body.code).toBe("TIME_OUT_OF_RANGE");
  });

  it("submits and lists problem-feedback tickets with contact details", async () => {
    const { app, token } = await authenticatedAgent();
    const payload = {
      category: "打卡问题",
      description: "完成打卡后页面没有刷新记录。",
      currentPage: "我的 / 问题反馈",
      clientVersion: "0.1.0-mvp",
      screenshots: ["proofs/feedback.png"],
      email: "student@example.com",
      phone: "+86 138 0000 0000"
    };
    const created = await request(app).post("/api/v1/student/feedback")
      .set("Authorization", `Bearer ${token}`).set("Idempotency-Key", "feedback-ticket-0001").send(payload);
    expect(created.status).toBe(201);
    expect(created.body).toMatchObject({ category: "打卡问题", email: payload.email, phone: payload.phone, status: "pending" });

    const listed = await request(app).get("/api/v1/student/feedback")
      .set("Authorization", `Bearer ${token}`);
    expect(listed.status).toBe(200);
    expect(listed.headers["cache-control"]).toContain("no-store");
    expect(listed.body.tickets).toHaveLength(1);
  });

  it("stores a student's email language preference", async () => {
    const store = new FakeStore();
    const { app, token } = await authenticatedAgent(store);

    const response = await request(app)
      .put("/api/v1/student/preferences/language")
      .set("Authorization", `Bearer ${token}`)
      .set("Idempotency-Key", "language-preference-0001")
      .send({ language: "en" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ language: "en" });
    expect(store.preferredLanguage).toBe("en");
  });

  it("registers and removes only an opaque Android FCM token", async () => {
    const store = new FakeStore();
    const { app, token } = await authenticatedAgent(store);
    const payload = {
      token: "fcm-token-for-device-address-only-1234567890",
      platform: "android",
      appVersion: "0.1.0-mvp"
    };
    expect(
      (await request(app).post("/api/v1/student/push-devices")
        .set("Authorization", `Bearer ${token}`).send(payload)).status
    ).toBe(204);
    expect(store.pushDeviceRegistrations).toEqual([{ studentId, input: payload }]);

    const rejected = await request(app).post("/api/v1/student/push-devices")
      .set("Authorization", `Bearer ${token}`)
      .send({ ...payload, title: "Student-specific text is not allowed" });
    expect(rejected.status).toBe(422);

    expect(
      (await request(app).delete("/api/v1/student/push-devices")
        .set("Authorization", `Bearer ${token}`).send(payload)).status
    ).toBe(204);
    expect(store.pushDeviceRemovals).toEqual([{ studentId, token: payload.token }]);
  });
});

describe("configuration and migrated passwords", () => {
  it("refuses insecure production configuration", () => {
    expect(() => loadConfig({ NODE_ENV: "production", DATABASE_URL: "mysql://u:p@localhost/db", JWT_SECRET: "replace-with-at-least-32-random-characters", PUBLIC_BASE_URL: "http://example.test", STORAGE_DRIVER: "local", CORS_ORIGINS: "" })).toThrow();
  });

  it("keeps cleanup batches large enough to service new and retry queues", () => {
    expect(() => testConfig({ UNCLAIMED_UPLOAD_CLEANUP_BATCH: "1" })).toThrow();
    expect(testConfig({ UNCLAIMED_UPLOAD_CLEANUP_BATCH: "2" }).UNCLAIMED_UPLOAD_CLEANUP_BATCH).toBe(2);
  });

  it("supports PBKDF2-SHA512 migration strings", async () => {
    const salt = "migration-salt";
    const digest = pbkdf2Sync("legacy-password", salt, 20_000, 64, "sha512").toString("hex");
    expect(await verifyPassword("legacy-password", `pbkdf2_sha512$20000$${salt}$${digest}`)).toBe(true);
    expect(await verifyPassword("wrong", `pbkdf2_sha512$20000$${salt}$${digest}`)).toBe(false);
    expect(createHash("sha256").update(digest).digest("hex")).toHaveLength(64);
  });

  it("uses a valid bcrypt dummy hash for unknown accounts", async () => {
    expect(await verifyPassword("not-the-password", DUMMY_PASSWORD_HASH)).toBe(true);
    expect(await verifyPassword("different-password", DUMMY_PASSWORD_HASH)).toBe(false);
  });
});

describe("safety primitives", () => {
  it("hashes normalized JSON independently of object key order", () => {
    expect(idempotencyRequestHash({ b: 2, a: { d: 4, c: 3 } })).toBe(
      idempotencyRequestHash({ a: { c: 3, d: 4 }, b: 2 })
    );
    expect(idempotencyRequestHash({ hours: 1 })).not.toBe(idempotencyRequestHash({ hours: 2 }));
  });

  it("caps upload concurrency and releases slots idempotently", () => {
    const gate = new ConcurrencyGate(2);
    const first = gate.tryAcquire();
    const second = gate.tryAcquire();
    expect(first).toBeTypeOf("function");
    expect(second).toBeTypeOf("function");
    expect(gate.tryAcquire()).toBeNull();
    first?.();
    first?.();
    expect(gate.inUse).toBe(1);
    expect(gate.tryAcquire()).toBeTypeOf("function");
  });

  it("detects tables that an unapplied migration would silently adopt", () => {
    const sql = "CREATE TABLE IF NOT EXISTS users (id INT); CREATE TABLE `sport_records` (id INT);";
    expect(createdTableNames(sql)).toEqual(["users", "sport_records"]);
    expect(untrackedTableConflicts(sql, ["schema_migrations", "USERS"])).toEqual(["users"]);
  });
});
