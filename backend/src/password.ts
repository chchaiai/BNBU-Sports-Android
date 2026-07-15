import { pbkdf2 as pbkdf2Callback, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import bcrypt from "bcryptjs";

const pbkdf2 = promisify(pbkdf2Callback);

// A valid cost-12 hash used to keep the unknown-account login path comparable to a real bcrypt check.
export const DUMMY_PASSWORD_HASH = "$2b$12$ZGSJ2n8B0Z1aNoPF4CIUvONu537v4yUBnprnZxqzbfOVzGSCOvAt6";

/**
 * New accounts use bcrypt. Migrated accounts may use the self-contained form
 * `pbkdf2_sha512$<iterations>$<salt>$<hex-or-base64-digest>`.
 */
export async function verifyPassword(password: string, encoded: string): Promise<boolean> {
  if (/^\$2[aby]\$/.test(encoded)) return bcrypt.compare(password, encoded);
  const match = /^pbkdf2_sha512\$(\d+)\$([^$]+)\$([^$]+)$/.exec(encoded);
  if (!match) return false;
  const iterations = Number(match[1]);
  const salt = match[2] ?? "";
  const digestText = match[3] ?? "";
  if (!Number.isSafeInteger(iterations) || iterations < 10_000 || iterations > 10_000_000 || !salt || !digestText) return false;
  const encoding = /^[a-fA-F0-9]+$/.test(digestText) && digestText.length % 2 === 0 ? "hex" : "base64";
  const expected = Buffer.from(digestText, encoding);
  if (expected.length < 32 || expected.length > 128) return false;
  const actual = await pbkdf2(password, salt, iterations, expected.length, "sha512");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
