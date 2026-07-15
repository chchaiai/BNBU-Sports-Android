import { randomUUID } from "node:crypto";
import type { RequestHandler } from "express";
import { z, type ZodType } from "zod";

export const requestId: RequestHandler = (request, response, next) => {
  const candidate = request.header("x-request-id");
  const id = candidate && /^[A-Za-z0-9._:-]{8,128}$/.test(candidate) ? candidate : randomUUID();
  request.id = id;
  response.setHeader("X-Request-Id", id);
  next();
};

export function parseBody<T>(schema: ZodType<T>, body: unknown): T {
  return schema.parse(body);
}

export const idParameter = z.string().trim().min(1).max(128);

export function asyncHandler(handler: Parameters<RequestHandler>[0] extends never ? never : RequestHandler): RequestHandler {
  return (request, response, next) => {
    Promise.resolve(handler(request, response, next)).catch(next);
  };
}
