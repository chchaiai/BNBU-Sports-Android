import "dotenv/config";
import http from "node:http";
import { createApp } from "./app";
import { loadConfig } from "./config/env";
import { MysqlStore } from "./db/mysql-store";
import { createPool } from "./db/pool";
import { getRequestLogContext } from "./http";
import { createLogger } from "./logger";
import { createStorage } from "./storage/storage";

const logger = createLogger();
let server: http.Server | undefined;
let store: MysqlStore | undefined;
let isShuttingDown = false;
let exitCode = 0;

process.on("unhandledRejection", (reason) => {
  handleFatalProcessError("unhandledRejection", reason);
});

process.on("uncaughtException", (error, origin) => {
  handleFatalProcessError("uncaughtException", error, origin);
});

process.on("SIGTERM", () => requestShutdown("SIGTERM", 0));
process.on("SIGINT", () => requestShutdown("SIGINT", 0));

async function main(): Promise<void> {
  const config = loadConfig();
  logger.level = config.LOG_LEVEL;

  const pool = createPool(config);
  const mysqlStore = new MysqlStore(pool);
  store = mysqlStore;
  const storage = createStorage(config);
  const app = createApp({ config, store: mysqlStore, storage, logger });
  server = http.createServer(app);

  server.requestTimeout = 310_000;
  server.headersTimeout = 315_000;
  server.keepAliveTimeout = 65_000;

  await listen(server, config.PORT, config.HOST);
  logger.info({ host: config.HOST, port: config.PORT }, "BNBU Sports API listening");
}

function handleFatalProcessError(event: "unhandledRejection" | "uncaughtException", error: unknown, origin?: string): void {
  const logContext = getRequestLogContext();
  logger.fatal({
    err: asError(error),
    event,
    ...(origin === undefined ? {} : { origin }),
    ...(error instanceof Error ? {} : { thrownValue: error }),
    ...(logContext ?? {})
  }, "Fatal process error; shutting down");
  requestShutdown(event, 1);
}

function requestShutdown(reason: string, requestedExitCode: number): void {
  exitCode = Math.max(exitCode, requestedExitCode);
  if (isShuttingDown) return;

  isShuttingDown = true;
  void shutdown(reason);
}

async function shutdown(reason: string): Promise<void> {
  logger.info({ reason, exitCode }, "Shutting down server");
  const forceExitTimer = setTimeout(() => {
    try {
      logger.fatal({ reason }, "Graceful shutdown timed out; forcing process exit");
      logger.flush();
    } finally {
      process.exit(1);
    }
  }, 15_000);
  forceExitTimer.unref();

  try {
    await closeServer();
    await store?.close();
  } catch (error) {
    exitCode = 1;
    logger.error({ err: asError(error) }, "Error while shutting down server");
  } finally {
    clearTimeout(forceExitTimer);
    await flushLogger();
    process.exit(exitCode);
  }
}

function listen(httpServer: http.Server, port: number, host: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const onError = (error: Error): void => reject(error);
    httpServer.once("error", onError);
    httpServer.listen(port, host, () => {
      httpServer.off("error", onError);
      resolve();
    });
  });
}

function closeServer(): Promise<void> {
  const httpServer = server;
  if (!httpServer) return Promise.resolve();

  return new Promise((resolve) => {
    httpServer.close((error) => {
      if (error) logger.error({ err: error }, "Error while closing HTTP server");
      resolve();
    });
    httpServer.closeIdleConnections?.();
  });
}

function asError(value: unknown): Error {
  if (value instanceof Error) return value;
  return new Error(`Non-Error value thrown: ${String(value)}`);
}

function flushLogger(): Promise<void> {
  return new Promise((resolve) => {
    try {
      logger.flush(() => resolve());
    } catch {
      resolve();
    }
  });
}

void main().catch((error) => {
  logger.fatal({ err: asError(error), event: "startup" }, "Server failed to start");
  requestShutdown("startup", 1);
});
