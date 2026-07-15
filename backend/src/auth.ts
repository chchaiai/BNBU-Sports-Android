import type { RequestHandler } from "express";
import jwt, { type JwtPayload } from "jsonwebtoken";
import { z } from "zod";
import type { AppConfig } from "./config/env";
import { AppError } from "./errors";
import type { BackendStore } from "./store";
import type { AuthenticatedUser, Role } from "./types";

const claimsSchema = z.object({
  sub: z.string().uuid(),
  role: z.enum(["student", "teacher", "admin"]),
  tokenVersion: z.number().int().nonnegative()
});

export function signAccessToken(config: AppConfig, user: { id: string; role: Role; tokenVersion: number }): string {
  return jwt.sign(
    { role: user.role, tokenVersion: user.tokenVersion },
    config.JWT_SECRET,
    {
      subject: user.id,
      issuer: config.JWT_ISSUER,
      audience: config.JWT_AUDIENCE,
      expiresIn: config.JWT_TTL as jwt.SignOptions["expiresIn"],
      algorithm: "HS256"
    }
  );
}

export function studentAuth(config: AppConfig, store: BackendStore): RequestHandler {
  return async (request, _response, next) => {
    try {
      const authorization = request.header("authorization");
      if (!authorization?.startsWith("Bearer ")) throw new AppError(401, "AUTH_REQUIRED", "未登录");
      const token = authorization.slice("Bearer ".length).trim();
      let decoded: string | JwtPayload;
      try {
        decoded = jwt.verify(token, config.JWT_SECRET, {
          issuer: config.JWT_ISSUER,
          audience: config.JWT_AUDIENCE,
          algorithms: ["HS256"]
        });
      } catch {
        throw new AppError(401, "TOKEN_INVALID", "登录已失效，请重新登录");
      }
      const claims = claimsSchema.parse(decoded);
      if (claims.role !== "student") throw new AppError(403, "ROLE_FORBIDDEN", "当前账号不是学生账号");
      const user = await store.findUserById(claims.sub);
      if (!user || user.role !== "student" || user.status !== "正常" || user.tokenVersion !== claims.tokenVersion) {
        throw new AppError(401, "TOKEN_REVOKED", "登录已失效，请重新登录");
      }
      request.auth = { id: user.id, role: user.role, tokenVersion: user.tokenVersion };
      next();
    } catch (error) {
      next(error);
    }
  };
}

export function studentId(request: { auth?: AuthenticatedUser }): string {
  if (!request.auth) throw new AppError(401, "AUTH_REQUIRED", "未登录");
  return request.auth.id;
}
