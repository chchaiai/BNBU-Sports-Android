import "dotenv/config";
import http from "node:http";
import { createApp } from "./app";
import { loadConfig } from "./config/env";
import { createPool } from "./db/pool";
import { MysqlStore } from "./db/mysql-store";
import { createStorage } from "./storage/storage";

async function main(): Promise<void> {
  const config = loadConfig();
  const pool = createPool(config);
  const store = new MysqlStore(pool);
  const storage = createStorage(config);
  const app = createApp({ config, store, storage });
  const server = http.createServer(app);

  server.requestTimeout = 310_000;
  server.headersTimeout = 315_000;
  server.keepAliveTimeout = 65_000;

  await new Promise<void>((resolve) => server.listen(config.PORT, config.HOST, resolve));
  console.info(`BNBU Sports API listening on ${config.HOST}:${config.PORT}`);

  const shutdown = async (signal: string): Promise<void> => {
    console.info(`${signal} received; shutting down`);
    server.close(async () => {
      await store.close();
      process.exit(0);
    });
    setTimeout(() => process.exit(1), 15_000).unref();
  };
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
  process.on("SIGINT", () => void shutdown("SIGINT"));
}

void main().catch((error) => {
  console.error("Server failed to start", error);
  process.exit(1);
});
