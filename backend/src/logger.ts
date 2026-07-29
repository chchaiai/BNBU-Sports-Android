import pino, { type Logger } from "pino";
import type { AppConfig } from "./config/env";

type LogLevel = AppConfig["LOG_LEVEL"];

const logLevels = new Set<LogLevel>([
  "fatal",
  "error",
  "warn",
  "info",
  "debug",
  "trace",
  "silent"
]);

/**
 * Creates the application logger with the same redaction policy for HTTP and
 * process-level logs. Invalid bootstrap values fall back to `info`; validated
 * configuration is applied by the server once it has loaded.
 */
export function createLogger(level: string | undefined = process.env.LOG_LEVEL): Logger {
  return pino({
    level: isLogLevel(level) ? level : "info",
    redact: {
      paths: ["req.headers.authorization", "req.headers.cookie"],
      censor: "[REDACTED]"
    }
  });
}

function isLogLevel(level: string | undefined): level is LogLevel {
  return level !== undefined && logLevels.has(level as LogLevel);
}
