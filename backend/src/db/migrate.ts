import "dotenv/config";
import { createHash } from "node:crypto";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import type { PoolConnection, RowDataPacket } from "mysql2/promise";
import { loadConfig } from "../config/env";
import { untrackedTableConflicts } from "./migration-safety";
import { createPool } from "./pool";

async function tableNames(connection: PoolConnection): Promise<string[]> {
  const [tables] = await connection.execute<RowDataPacket[]>(
    `SELECT TABLE_NAME AS table_name
       FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'`
  );
  return tables.map((row) => String(row.table_name));
}

async function assertMigrationLedgerShape(connection: PoolConnection): Promise<void> {
  const [columns] = await connection.execute<RowDataPacket[]>(
    `SELECT COLUMN_NAME AS column_name, DATA_TYPE AS data_type,
            CHARACTER_MAXIMUM_LENGTH AS max_length, IS_NULLABLE AS is_nullable
       FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schema_migrations'`
  );
  const actual = new Map(columns.map((row) => [String(row.column_name).toLowerCase(), row]));
  const issues: string[] = [];
  const expected = [
    { name: "name", type: "varchar", length: 255 },
    { name: "checksum", type: "char", length: 64 },
    { name: "applied_at", type: "datetime", length: null }
  ];
  for (const definition of expected) {
    const column = actual.get(definition.name);
    if (!column) {
      issues.push(`missing ${definition.name}`);
      continue;
    }
    if (String(column.data_type).toLowerCase() !== definition.type) issues.push(`${definition.name} has the wrong type`);
    if (definition.length !== null && Number(column.max_length) !== definition.length) issues.push(`${definition.name} has the wrong length`);
    if (String(column.is_nullable).toUpperCase() !== "NO") issues.push(`${definition.name} must be NOT NULL`);
  }
  const [primaryRows] = await connection.execute<RowDataPacket[]>(
    `SELECT COLUMN_NAME AS column_name
       FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schema_migrations' AND INDEX_NAME = 'PRIMARY'
      ORDER BY SEQ_IN_INDEX`
  );
  if (primaryRows.map((row) => String(row.column_name).toLowerCase()).join(",") !== "name") {
    issues.push("primary key must be exactly (name)");
  }
  if (issues.length > 0) {
    throw new Error(`Existing schema_migrations table is incompatible: ${issues.join("; ")}`);
  }
}

async function migrate(): Promise<void> {
  const config = loadConfig();
  const pool = createPool(config);
  let connection: PoolConnection | undefined;
  let lockName: string | undefined;
  let locked = false;
  try {
    connection = await pool.getConnection();
    const [databaseRows] = await connection.execute<RowDataPacket[]>("SELECT DATABASE() AS database_name");
    const databaseName = String(databaseRows[0]?.database_name ?? "unknown");
    lockName = `bnbu-migrate:${createHash("sha256").update(databaseName).digest("hex").slice(0, 32)}`;
    const [lockRows] = await connection.execute<RowDataPacket[]>("SELECT GET_LOCK(?, 30) AS acquired", [lockName]);
    locked = Number(lockRows[0]?.acquired) === 1;
    if (!locked) throw new Error("Could not acquire the database migration advisory lock within 30 seconds");

    await connection.execute(`CREATE TABLE IF NOT EXISTS schema_migrations (
      name VARCHAR(255) PRIMARY KEY,
      checksum CHAR(64) NOT NULL,
      applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci`);
    await assertMigrationLedgerShape(connection);

    const directory = path.resolve(process.cwd(), "db/migrations");
    const files = (await readdir(directory)).filter((file) => file.endsWith(".sql")).sort();
    for (const file of files) {
      const sql = await readFile(path.join(directory, file), "utf8");
      const checksum = createHash("sha256").update(sql).digest("hex");
      const [existing] = await connection.execute<RowDataPacket[]>(
        "SELECT checksum FROM schema_migrations WHERE name = ?",
        [file]
      );
      if (existing[0]) {
        if (String(existing[0].checksum) !== checksum) throw new Error(`Applied migration changed: ${file}`);
        continue;
      }

      const conflicts = untrackedTableConflicts(sql, await tableNames(connection));
      if (conflicts.length > 0) {
        throw new Error(
          `Refusing to apply ${file}: untracked tables already exist (${conflicts.join(", ")}). ` +
          "Verify their schema and create an explicit baseline migration; they will not be adopted automatically."
        );
      }

      try {
        for (const statement of sql.split("-- statement-breakpoint").map((value) => value.trim()).filter(Boolean)) {
          await connection.query(statement);
        }
        await connection.execute("INSERT INTO schema_migrations (name, checksum) VALUES (?, ?)", [file, checksum]);
        console.info(`Applied ${file}`);
      } catch (error) {
        throw new Error(
          `Migration ${file} failed. MySQL DDL is not transactional; inspect and repair any partially created tables before retrying.`,
          { cause: error }
        );
      }
    }
  } finally {
    if (connection) {
      if (locked && lockName) await connection.execute("SELECT RELEASE_LOCK(?)", [lockName]).catch(() => undefined);
      connection.release();
    }
    await pool.end();
  }
}

void migrate().catch((error) => {
  console.error(error);
  process.exit(1);
});
