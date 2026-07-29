import path from "node:path";
import cors from "cors";
import express, { type Express } from "express";
import rateLimit from "express-rate-limit";
import helmet from "helmet";
import type { Logger } from "pino";
import pinoHttp from "pino-http";
import type { AppConfig } from "./config/env";
import { AppError, errorHandler, notFoundHandler } from "./errors";
import { requestId, requestLogContextMiddleware } from "./http";
import { createLogger } from "./logger";
import { createApiRouter } from "./routes";
import type { BackendStore } from "./store";
import type { ObjectStorage } from "./storage/storage";

export interface AppDependencies {
  config: AppConfig;
  store: BackendStore;
  storage: ObjectStorage;
  logger?: Logger;
}

export function createApp({ config, store, storage, logger }: AppDependencies): Express {
  const app = express();
  const appLogger = logger ?? createLogger(config.LOG_LEVEL);
  app.disable("x-powered-by");
  if (config.TRUST_PROXY) app.set("trust proxy", 1);

  app.use(requestId);
  app.use(requestLogContextMiddleware);
  app.use(pinoHttp({ logger: appLogger, genReqId: (request) => request.id }));
  app.use(helmet({ crossOriginResourcePolicy: { policy: "same-site" } }));
  app.use(cors({
    credentials: true,
    methods: ["GET", "POST", "PUT", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization", "Idempotency-Key", "X-Request-Id"],
    maxAge: 86_400,
    origin(origin, callback) {
      if (!origin || config.corsOrigins.includes(origin)) return callback(null, true);
      callback(new AppError(403, "CORS_ORIGIN_DENIED", "该网页来源不允许访问此服务"));
    }
  }));
  app.use(rateLimit({
    windowMs: 15 * 60_000,
    limit: config.GLOBAL_RATE_LIMIT_PER_15_MIN,
    standardHeaders: "draft-7",
    legacyHeaders: false,
    // Orchestrators can share an IP with high-volume client traffic. Never let
    // that traffic turn a healthy instance into a failed liveness/readiness probe.
    skip: (request) => request.method === "GET" && request.path === "/api/health",
    handler: (_request, _response, next) => next(new AppError(429, "RATE_LIMITED", "请求过于频繁，请稍后再试"))
  }));
  app.use(express.json({ limit: "1mb", strict: true }));
  app.use(express.urlencoded({ extended: false, limit: "32kb" }));

  if (config.STORAGE_DRIVER === "local") {
    app.use("/uploads", express.static(path.resolve(config.UPLOAD_LOCAL_DIR), {
      dotfiles: "deny",
      fallthrough: false,
      immutable: true,
      maxAge: "1d",
      setHeaders(response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
      }
    }));
  }

  app.use("/api", createApiRouter(config, store, storage));
  app.use(notFoundHandler);
  app.use(errorHandler);
  return app;
}
