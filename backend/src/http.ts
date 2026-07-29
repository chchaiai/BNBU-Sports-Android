import { AsyncLocalStorage } from "node:async_hooks";
import { randomUUID } from "node:crypto";
import type { Request, RequestHandler } from "express";
import { z, type ZodType } from "zod";

interface RequestLogContext {
  requestId: string;
  userId?: string;
}

interface ActiveRequestLogContext {
  requestId: string;
  request: Request;
}

const requestLogContext = new AsyncLocalStorage<ActiveRequestLogContext>();

export const requestId: RequestHandler = (request, response, next) => {
  const candidate = request.header("x-request-id");
  const id = candidate && /^[A-Za-z0-9._:-]{8,128}$/.test(candidate) ? candidate : randomUUID();
  request.id = id;
  response.setHeader("X-Request-Id", id);
  next();
};

/** Keeps request identifiers available to asynchronous work and fatal process handlers. */
export const requestLogContextMiddleware: RequestHandler = (request, _response, next) => {
  requestLogContext.run({ requestId: String(request.id), request }, next);
};

/** Returns a copy so log call sites cannot mutate the active request context. */
export function getRequestLogContext(): RequestLogContext | undefined {
  const context = requestLogContext.getStore();
  if (!context) return undefined;

  return {
    requestId: context.requestId,
    ...(context.request.auth?.id === undefined ? {} : { userId: context.request.auth.id })
  };
}

export function parseBody<T>(schema: ZodType<T>, body: unknown): T {
  return schema.parse(body);
}

export const idParameter = z.string().trim().min(1).max(128);

export function asyncHandler(handler: Parameters<RequestHandler>[0] extends never ? never : RequestHandler): RequestHandler {
  return (request, response, next) => {
    Promise.resolve(handler(request, response, next)).catch(next);
  };
}
