import type { ErrorRequestHandler, RequestHandler } from "express";
import multer from "multer";
import { ZodError } from "zod";

export class AppError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = "AppError";
  }
}

export const notFoundHandler: RequestHandler = (request, _response, next) => {
  next(new AppError(404, "NOT_FOUND", `接口不存在: ${request.method} ${request.path}`));
};

export const errorHandler: ErrorRequestHandler = (error, request, response, _next) => {
  let normalized: AppError;
  if (error instanceof AppError) {
    normalized = error;
  } else if (error instanceof ZodError) {
    normalized = new AppError(422, "VALIDATION_ERROR", "请求参数不合法", error.flatten());
  } else if (error instanceof multer.MulterError) {
    const message = error.code === "LIMIT_FILE_SIZE" ? "文件超过 100MB 上限" : `上传失败: ${error.message}`;
    normalized = new AppError(413, "UPLOAD_LIMIT", message);
  } else if (isHttpParserError(error, 413)) {
    normalized = new AppError(413, "REQUEST_TOO_LARGE", "请求体超过服务允许的大小");
  } else if (isHttpParserError(error, 400)) {
    normalized = new AppError(400, "INVALID_JSON", "请求体不是有效的 JSON");
  } else {
    request.log?.error({ err: error }, "unhandled request error");
    normalized = new AppError(500, "INTERNAL_ERROR", "服务器内部错误");
  }

  response.status(normalized.status).json({
    code: normalized.code,
    message: normalized.message,
    requestId: request.id,
    ...(normalized.details === undefined ? {} : { details: normalized.details })
  });
};

function isHttpParserError(error: unknown, status: number): boolean {
  if (error === null || typeof error !== "object") return false;
  const candidate = error as { status?: unknown; statusCode?: unknown; type?: unknown };
  const actualStatus = candidate.status ?? candidate.statusCode;
  if (actualStatus !== status) return false;
  return typeof candidate.type === "string" && candidate.type.startsWith("entity.");
}
