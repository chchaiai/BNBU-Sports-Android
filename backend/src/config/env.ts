import { z } from "zod";

const booleanString = z
  .enum(["0", "1", "true", "false"])
  .default("1")
  .transform((value) => value === "1" || value === "true");

const schema = z
  .object({
    NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
    HOST: z.string().min(1).default("127.0.0.1"),
    PORT: z.coerce.number().int().min(1).max(65535).default(3005),
    PUBLIC_BASE_URL: z.string().url().default("http://localhost:3005"),
    DATABASE_URL: z.string().url().refine((value) => value.startsWith("mysql://"), {
      message: "DATABASE_URL must use mysql://"
    }),
    JWT_SECRET: z.string().min(32),
    JWT_ISSUER: z.string().min(1).default("bnbu-sports"),
    JWT_AUDIENCE: z.string().min(1).default("bnbu-student"),
    JWT_TTL: z.string().min(2).default("12h"),
    CORS_ORIGINS: z.string().default(""),
    TRUST_PROXY: booleanString,
    LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),
    GLOBAL_RATE_LIMIT_PER_15_MIN: z.coerce.number().int().min(1).max(1_000_000).default(60_000),
    LOGIN_RATE_LIMIT_PER_15_MIN: z.coerce.number().int().min(1).max(1_000).default(20),
    STORAGE_DRIVER: z.enum(["local", "cos"]).default("local"),
    UPLOAD_LOCAL_DIR: z.string().min(1).default("./uploads"),
    UPLOAD_MAX_REQUEST_BYTES: z.coerce.number().int().min(1_000_000).max(200_000_000).default(120_000_000),
    UPLOAD_MAX_CONCURRENT: z.coerce.number().int().min(1).max(32).default(2),
    UNCLAIMED_UPLOAD_TTL_HOURS: z.coerce.number().int().min(1).max(168).default(24),
    UNCLAIMED_UPLOAD_CLEANUP_BATCH: z.coerce.number().int().min(2).max(500).default(100),
    COS_SECRET_ID: z.string().optional().default(""),
    COS_SECRET_KEY: z.string().optional().default(""),
    COS_BUCKET: z.string().optional().default(""),
    COS_REGION: z.string().optional().default(""),
    COS_SIGNED_URL_TTL_SECONDS: z.coerce.number().int().min(60).max(86400).default(900),
    SEED_STUDENT_ACCOUNT: z.string().optional(),
    SEED_STUDENT_EMAIL: z.string().email().optional(),
    SEED_STUDENT_PASSWORD: z.string().min(12).optional()
  })
  .superRefine((value, context) => {
    if (value.NODE_ENV === "production") {
      if (!value.PUBLIC_BASE_URL.startsWith("https://")) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ["PUBLIC_BASE_URL"], message: "Production PUBLIC_BASE_URL must use HTTPS" });
      }
      if (value.STORAGE_DRIVER !== "cos") {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ["STORAGE_DRIVER"], message: "Production storage must use COS" });
      }
      if (!value.CORS_ORIGINS.split(",").some((origin) => origin.trim().startsWith("https://"))) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ["CORS_ORIGINS"], message: "Production requires at least one HTTPS CORS origin" });
      }
      if (/replace|change-me|example/i.test(value.JWT_SECRET)) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: ["JWT_SECRET"], message: "Production JWT secret is still a placeholder" });
      }
    }
    if (value.STORAGE_DRIVER !== "cos") return;
    for (const field of ["COS_SECRET_ID", "COS_SECRET_KEY", "COS_BUCKET", "COS_REGION"] as const) {
      if (!value[field]) {
        context.addIssue({ code: z.ZodIssueCode.custom, path: [field], message: `${field} is required for COS storage` });
      }
    }
  });

export type AppConfig = Omit<z.infer<typeof schema>, "CORS_ORIGINS"> & {
  corsOrigins: string[];
};

export function loadConfig(environment: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = schema.parse(environment);
  const { CORS_ORIGINS, ...config } = parsed;
  return {
    ...config,
    corsOrigins: CORS_ORIGINS.split(",").map((origin) => origin.trim()).filter(Boolean)
  };
}
