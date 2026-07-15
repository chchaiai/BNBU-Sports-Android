import mysql, { type Pool } from "mysql2/promise";
import type { AppConfig } from "../config/env";

export function createPool(config: Pick<AppConfig, "DATABASE_URL">): Pool {
  return mysql.createPool({
    uri: config.DATABASE_URL,
    connectionLimit: 10,
    maxIdle: 10,
    idleTimeout: 60_000,
    enableKeepAlive: true,
    keepAliveInitialDelay: 0,
    decimalNumbers: true,
    dateStrings: true,
    timezone: "Z",
    charset: "utf8mb4"
  });
}
