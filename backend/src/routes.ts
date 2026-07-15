import { randomUUID } from "node:crypto";
import { Router } from "express";
import rateLimit from "express-rate-limit";
import multer from "multer";
import { z } from "zod";
import { ConcurrencyGate } from "./concurrency";
import type { AppConfig } from "./config/env";
import { AppError } from "./errors";
import { asyncHandler, idParameter, parseBody } from "./http";
import { idempotencyRequestHash } from "./idempotency";
import { signAccessToken, studentAuth, studentId } from "./auth";
import type { BackendStore } from "./store";
import { createProofObjectKey, type ObjectStorage } from "./storage/storage";
import { validateUploads } from "./storage/validation";
import { DUMMY_PASSWORD_HASH, verifyPassword } from "./password";

const loginSchema = z.object({
  account: z.string().trim().min(1).max(254),
  password: z.string().min(1).max(256),
  role: z.literal("student").default("student"),
  clientType: z.literal("mobile").default("mobile")
}).strict();

const proofReferenceSchema = z.object({
  cosKey: z.string().trim().min(1).max(1024),
  mediaType: z.enum(["image", "video"]),
  mimeType: z.string().trim().min(1).max(128),
  size: z.coerce.number().int().nonnegative().max(100_000_000)
}).strict();

const creditTypeSchema = z.enum(["课程相关", "其他运动", "course", "general"]).transform((value) => {
  if (value === "course") return "课程相关" as const;
  if (value === "general") return "其他运动" as const;
  return value;
});

const submitRecordSchema = z.object({
  creditType: creditTypeSchema,
  courseId: z.string().trim().min(1).max(128).nullable().optional().default(null),
  taskId: z.string().trim().min(1).max(128).nullable().optional().default(null),
  hours: z.coerce.number().refine((value) => value === 1 || value === 2, "hours must be 1 or 2"),
  description: z.string().trim().max(2000).default(""),
  proofFiles: z.array(proofReferenceSchema).min(1).max(7),
  sportType: z.string().trim().min(1).max(100).nullable().optional().default(null)
}).strict().superRefine((value, context) => {
  if (value.creditType === "课程相关" && !value.courseId) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ["courseId"], message: "课程相关打卡必须指定课程" });
  }
  if (value.creditType === "其他运动" && (value.courseId || value.taskId)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: [value.courseId ? "courseId" : "taskId"],
      message: "其他运动打卡不能关联课程或课程任务"
    });
  }
});

const supplementSchema = z.object({
  hours: z.coerce.number().refine((value) => value === 1 || value === 2, "hours must be 1 or 2"),
  description: z.string().trim().max(2000).default(""),
  proofFiles: z.array(proofReferenceSchema).min(1).max(7)
}).strict();

const profileSchema = z.object({
  gender: z.enum(["male", "female"]).nullable()
}).strict();

const exemptionSchema = z.object({
  type: z.enum(["800m", "1000m", "team", "club"]),
  reason: z.string().trim().min(2).max(2000),
  proofFiles: z.array(z.string().trim().min(1).max(1024)).min(1).max(5),
  organization: z.string().trim().min(1).max(128).nullable().optional().default(null)
}).strict();

const exemptionSupplementSchema = z.object({
  reason: z.string().trim().min(2).max(2000),
  proofFiles: z.array(z.string().trim().min(1).max(1024)).min(1).max(5),
  organization: z.string().trim().min(1).max(128).nullable().optional().default(null)
}).strict();

const enduranceSchema = z.object({
  timeSeconds: z.coerce.number().int().min(1).max(3600),
  gender: z.enum(["male", "female"]),
  gradeLevel: z.enum(["freshman", "sophomore", "junior", "senior"])
}).strict();

const pageQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(500).default(500),
  offset: z.coerce.number().int().min(0).max(1_000_000).default(0)
});

const idempotencyKey = (header: string | undefined): string | undefined => {
  if (!header) return undefined;
  if (!/^[A-Za-z0-9._:-]{8,128}$/.test(header)) throw new AppError(422, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key 格式不正确");
  return header;
};

const mutationIdempotency = (header: string | undefined, payload: unknown): { idempotencyKey?: string; idempotencyHash?: string } => {
  const key = idempotencyKey(header);
  return key ? { idempotencyKey: key, idempotencyHash: idempotencyRequestHash(payload) } : {};
};

async function refreshProofUrls(value: unknown, storage: ObjectStorage): Promise<unknown> {
  if (Array.isArray(value)) return Promise.all(value.map((item) => refreshProofUrls(item, storage)));
  if (value === null || typeof value !== "object") return value;
  const source = value as Record<string, unknown>;
  const refreshed = Object.fromEntries(await Promise.all(
    Object.entries(source).map(async ([key, item]) => [key, await refreshProofUrls(item, storage)])
  ));
  if (typeof source.cosKey === "string" && Object.hasOwn(source, "url")) {
    refreshed.url = await storage.url(source.cosKey);
  }
  return refreshed;
}

export function createApiRouter(config: AppConfig, store: BackendStore, storage: ObjectStorage): Router {
  const router = Router();
  const uploadGate = new ConcurrencyGate(config.UPLOAD_MAX_CONCURRENT);
  const loginLimiter = rateLimit({
    windowMs: 15 * 60_000,
    limit: config.LOGIN_RATE_LIMIT_PER_15_MIN,
    standardHeaders: "draft-7",
    legacyHeaders: false,
    keyGenerator(request) {
      const account = typeof request.body?.account === "string" ? request.body.account.trim().toLowerCase() : "";
      return account ? `account:${account}` : `missing-account:${request.ip ?? "unknown"}`;
    },
    handler: (_request, _response, next) => next(new AppError(429, "LOGIN_RATE_LIMITED", "登录尝试过多，请稍后再试")),
    message: { code: "RATE_LIMITED", message: "登录尝试过多，请稍后再试" }
  });

  router.get("/health", asyncHandler(async (_request, response) => {
    const [db, cos] = await Promise.all([store.ping(), storage.health()]);
    response.setHeader("Cache-Control", "no-cache");
    response.status(db && cos ? 200 : 503).json({
      ok: db && cos,
      service: "BNBU Sports API",
      db,
      cos,
      time: new Date().toISOString()
    });
  }));

  router.post("/auth/login", loginLimiter, asyncHandler(async (request, response) => {
    const input = parseBody(loginSchema, request.body);
    const user = await store.findUserByAccount(input.account);
    const valid = user ? await verifyPassword(input.password, user.passwordHash) : await verifyPassword(input.password, DUMMY_PASSWORD_HASH);
    if (!user || !valid) throw new AppError(401, "INVALID_CREDENTIALS", "账号或密码错误");
    if (user.role !== "student") throw new AppError(403, "ROLE_FORBIDDEN", "请使用学生账号登录");
    if (user.status !== "正常") throw new AppError(403, "ACCOUNT_DISABLED", "账号已停用，请联系管理员");
    const token = signAccessToken(config, user);
    response.json({
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        role: user.role,
        college: user.college,
        scope: "student",
        status: user.status,
        gender: user.gender,
        gradeLevel: user.gradeLevel,
        className: user.className
      },
      defaultRoute: "/student"
    });
  }));

  router.use(studentAuth(config, store));

  router.get("/sport/summary", asyncHandler(async (request, response) => {
    response.json(await store.getSportSummary(studentId(request)));
  }));

  router.get("/sport/records", asyncHandler(async (request, response) => {
    const query = pageQuerySchema.extend({
      status: z.string().max(32).optional(),
      courseId: z.string().max(128).optional()
    }).parse(request.query);
    const records = await store.listSportRecords(studentId(request), query);
    response.json(await refreshProofUrls(records, storage));
  }));

  router.post("/sport/records", asyncHandler(async (request, response) => {
    const input = parseBody(submitRecordSchema, request.body);
    const result = await store.createSportRecord(studentId(request), {
      creditType: input.creditType as "课程相关" | "其他运动",
      courseId: input.courseId ?? null,
      taskId: input.taskId ?? null,
      hours: input.hours,
      description: input.description ?? "",
      proofFiles: input.proofFiles,
      sportType: input.sportType ?? null,
      ...mutationIdempotency(request.header("idempotency-key"), input)
    });
    response.status(201).json(result);
  }));

  router.get("/sport/records/:id", asyncHandler(async (request, response) => {
    const id = idParameter.parse(request.params.id);
    const record = await store.getSportRecord(studentId(request), id);
    if (!record) throw new AppError(404, "RECORD_NOT_FOUND", "打卡记录不存在");
    response.json(await refreshProofUrls(record, storage));
  }));

  router.post("/sport/records/:id/supplements", asyncHandler(async (request, response) => {
    const id = idParameter.parse(request.params.id);
    const input = parseBody(supplementSchema, request.body);
    response.status(201).json(await store.supplementSportRecord(studentId(request), id, {
      hours: input.hours,
      description: input.description ?? "",
      proofFiles: input.proofFiles,
      ...mutationIdempotency(request.header("idempotency-key"), input)
    }));
  }));

  router.get("/sport/identity", asyncHandler(async (request, response) => {
    response.json(await store.listIdentities(studentId(request)));
  }));

  router.get("/common/notifications", asyncHandler(async (request, response) => {
    response.json(await store.listNotifications(studentId(request)));
  }));

  router.put("/common/notifications/:id/read", asyncHandler(async (request, response) => {
    const id = idParameter.parse(request.params.id);
    const result = await store.markNotificationRead(studentId(request), id);
    if (!result) throw new AppError(404, "NOTIFICATION_NOT_FOUND", "通知不存在");
    response.json(result);
  }));

  router.get("/student/profile", asyncHandler(async (request, response) => {
    response.json(await store.getProfile(studentId(request)));
  }));

  router.put("/student/profile", asyncHandler(async (request, response) => {
    const input = parseBody(profileSchema, request.body);
    response.json(await store.updateProfile(studentId(request), input.gender));
  }));

  router.get("/student/courses", asyncHandler(async (request, response) => {
    const query = z.object({
      scope: z.enum(["all", "current", "history"]).default("all"),
      semesterId: z.string().trim().min(1).max(128).optional()
    }).parse(request.query);
    response.json(await store.listCourses(studentId(request), query.scope, query.semesterId));
  }));

  router.get("/student/tasks", asyncHandler(async (request, response) => {
    response.json(await store.listTasks(studentId(request)));
  }));

  router.get("/student/grades", asyncHandler(async (request, response) => {
    response.json(await store.getGrades(studentId(request)));
  }));

  const listExemptions = (category?: "physical_test" | "checkin") => asyncHandler(async (request, response) => {
    const page = pageQuerySchema.parse(request.query);
    const exemptions = await store.listExemptions(studentId(request), category, page);
    response.json(await refreshProofUrls(exemptions, storage));
  });
  const submitExemption = (category: "physical_test" | "checkin") => asyncHandler(async (request, response) => {
    const input = parseBody(exemptionSchema, request.body);
    if (category === "physical_test" && input.organization !== null) {
      throw new AppError(422, "ORGANIZATION_NOT_ALLOWED", "体测免测不能填写组织名称");
    }
    if (category === "physical_test" && !["800m", "1000m"].includes(input.type)) throw new AppError(422, "INVALID_EXEMPTION_TYPE", "体测免测只支持 800m 或 1000m");
    if (category === "checkin" && !["team", "club"].includes(input.type)) throw new AppError(422, "INVALID_EXEMPTION_TYPE", "免打卡只支持校队或社团");
    if (category === "checkin" && !input.organization) throw new AppError(422, "ORGANIZATION_REQUIRED", "请填写组织名称");
    response.status(201).json(await store.createExemption(studentId(request), category, {
      type: input.type,
      reason: input.reason,
      proofFiles: input.proofFiles,
      organization: input.organization ?? null,
      ...mutationIdempotency(request.header("idempotency-key"), input)
    }));
  });
  const supplementExemption = (category: "physical_test" | "checkin") => asyncHandler(async (request, response) => {
    const exemptionId = idParameter.parse(request.params.id);
    const input = parseBody(exemptionSupplementSchema, request.body);
    if (category === "physical_test" && input.organization !== null) {
      throw new AppError(422, "ORGANIZATION_NOT_ALLOWED", "体测免测补充材料不能填写组织名称");
    }
    if (category === "checkin" && !input.organization) {
      throw new AppError(422, "ORGANIZATION_REQUIRED", "免打卡补充材料必须填写组织名称");
    }
    response.status(201).json(await store.supplementExemption(studentId(request), exemptionId, category, {
      reason: input.reason,
      proofFiles: input.proofFiles,
      organization: input.organization ?? null,
      ...mutationIdempotency(request.header("idempotency-key"), input)
    }));
  });

  router.get("/student/exemptions", listExemptions("physical_test"));
  router.post("/student/exemptions", submitExemption("physical_test"));
  router.get("/student/physical-test-exemptions", listExemptions("physical_test"));
  router.post("/student/physical-test-exemptions", submitExemption("physical_test"));
  router.post("/student/physical-test-exemptions/:id/supplements", supplementExemption("physical_test"));
  router.get("/student/checkin-exemptions", listExemptions("checkin"));
  router.post("/student/checkin-exemptions", submitExemption("checkin"));
  router.post("/student/checkin-exemptions/:id/supplements", supplementExemption("checkin"));

  router.post("/scoring/convert-endurance", asyncHandler(async (request, response) => {
    const input = parseBody(enduranceSchema, request.body);
    const result = await store.convertEndurance(input.timeSeconds, input.gender, input.gradeLevel);
    if (!result) throw new AppError(422, "TIME_OUT_OF_RANGE", "该成绩超出有效换算区间");
    response.json(result);
  }));

  const upload = multer({
    storage: multer.memoryStorage(),
    limits: { files: 7, fileSize: 100_000_000, fields: 0, parts: 7 }
  });
  router.post("/upload/proof", (request, _response, next) => {
    const release = uploadGate.tryAcquire();
    if (!release) return next(new AppError(429, "UPLOAD_BUSY", "当前上传任务较多，请稍后重试"));
    _response.once("finish", release);
    _response.once("close", release);
    const rawLength = request.header("content-length");
    const length = rawLength ? Number(rawLength) : NaN;
    if (config.NODE_ENV === "production" && !Number.isFinite(length)) {
      return next(new AppError(411, "LENGTH_REQUIRED", "上传请求必须提供 Content-Length"));
    }
    if (Number.isFinite(length) && length > config.UPLOAD_MAX_REQUEST_BYTES) {
      return next(new AppError(413, "REQUEST_TOO_LARGE", "单次上传请求不能超过 120MB"));
    }
    next();
  }, upload.array("files", 7), asyncHandler(async (request, response) => {
    const files = validateUploads((request.files as Express.Multer.File[] | undefined) ?? [], config.UPLOAD_MAX_REQUEST_BYTES);
    const ownerId = studentId(request);
    // Reserve every object key in MySQL before touching object storage. If the
    // process or compensation later fails, the cleanup job still has a durable
    // row from which it can retry deletion.
    const planned = await Promise.all(files.map(async (file) => {
      const cosKey = createProofObjectKey(ownerId, file.mimeType);
      return {
        url: await storage.url(cosKey),
        cosKey,
        mediaType: file.mediaType,
        mimeType: file.mimeType,
        size: file.size
      };
    }));
    await store.registerProofFiles(ownerId, planned);
    const uploaded: Awaited<ReturnType<ObjectStorage["put"]>>[] = [];
    try {
      for (const [index, file] of files.entries()) {
        const proof = await storage.put(ownerId, file, planned[index]!.cosKey);
        if (proof.cosKey !== planned[index]!.cosKey) throw new Error("Object storage returned an unexpected proof key");
        uploaded.push(proof);
      }
    } catch (error) {
      // Rows were registered first, so even a failed compensation remains
      // discoverable by cleanup:proofs and cannot become a permanent orphan.
      await Promise.allSettled(uploaded.map((proof) => storage.delete(proof.cosKey)));
      throw error;
    }
    response.status(201).json({ files: uploaded, count: uploaded.length, uploadId: randomUUID() });
  }));

  return router;
}
