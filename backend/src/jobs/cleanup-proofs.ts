import "dotenv/config";
import { createHash } from "node:crypto";
import type { PoolConnection, ResultSetHeader, RowDataPacket } from "mysql2/promise";
import { loadConfig } from "../config/env";
import { createPool } from "../db/pool";
import { createStorage } from "../storage/storage";

const RETRY_COOLDOWN_MINUTES = 15;

async function retryCandidates(connection: PoolConnection, limit: number, offset = 0): Promise<string[]> {
  if (limit <= 0) return [];
  const [rows] = await connection.execute<RowDataPacket[]>(
    `SELECT cos_key
       FROM proof_files
      WHERE owner_type = 'deleting'
        AND (cleanup_last_attempt_at IS NULL
          OR cleanup_last_attempt_at < TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP(3)))
      ORDER BY COALESCE(cleanup_last_attempt_at, '1970-01-01 00:00:00'), created_at, cos_key
      LIMIT ? OFFSET ?`,
    [RETRY_COOLDOWN_MINUTES, limit, offset]
  );
  return rows.map((row) => String(row.cos_key));
}

async function claimStaleUploads(
  connection: PoolConnection,
  ttlHours: number,
  limit: number
): Promise<string[]> {
  if (limit <= 0) return [];
  await connection.beginTransaction();
  try {
    const [rows] = await connection.execute<RowDataPacket[]>(
      `SELECT cos_key
         FROM proof_files
        WHERE owner_type = 'upload' AND owner_id IS NULL
          AND created_at < TIMESTAMPADD(HOUR, -?, CURRENT_TIMESTAMP(3))
        ORDER BY created_at, cos_key
        LIMIT ? FOR UPDATE SKIP LOCKED`,
      [ttlHours, limit]
    );
    const keys = rows.map((row) => String(row.cos_key));
    if (keys.length > 0) {
      const placeholders = keys.map(() => "?").join(",");
      const [updated] = await connection.execute<ResultSetHeader>(
        `UPDATE proof_files
            SET owner_type = 'deleting', owner_id = NULL
          WHERE owner_type = 'upload' AND owner_id IS NULL
            AND cos_key IN (${placeholders})`,
        keys
      );
      if (updated.affectedRows !== keys.length) {
        throw new Error("Could not persist every proof deletion tombstone");
      }
    }
    await connection.commit();
    return keys;
  } catch (error) {
    await connection.rollback();
    throw error;
  }
}

async function markFailure(connection: PoolConnection, cosKey: string): Promise<void> {
  await connection.execute(
    `UPDATE proof_files
        SET cleanup_attempts = cleanup_attempts + 1,
            cleanup_last_attempt_at = CURRENT_TIMESTAMP(3)
      WHERE cos_key = ? AND owner_type = 'deleting'`,
    [cosKey]
  );
}

async function main(): Promise<void> {
  const config = loadConfig();
  const pool = createPool(config);
  const storage = createStorage(config);
  let connection: PoolConnection | undefined;
  let cleanupLock: string | undefined;
  let locked = false;
  let removed = 0;
  const failures: Array<{ cosKey: string; message: string }> = [];

  try {
    connection = await pool.getConnection();
    const [databaseRows] = await connection.execute<RowDataPacket[]>("SELECT DATABASE() AS database_name");
    const databaseName = String(databaseRows[0]?.database_name ?? "unknown");
    cleanupLock = `bnbu-proof-cleanup:${createHash("sha256").update(databaseName).digest("hex").slice(0, 32)}`;
    const [lockRows] = await connection.execute<RowDataPacket[]>(
      "SELECT GET_LOCK(?, 0) AS acquired",
      [cleanupLock]
    );
    locked = Number(lockRows[0]?.acquired) === 1;
    if (!locked) {
      process.stdout.write("Proof cleanup skipped because another worker holds the lock.\n");
      return;
    }

    // Reserve half the batch for retries and half for newly stale uploads. This
    // prevents one permanently failing object from starving every later row.
    const retryQuota = Math.floor(config.UNCLAIMED_UPLOAD_CLEANUP_BATCH / 2);
    const retries = await retryCandidates(connection, retryQuota);
    const stale = await claimStaleUploads(
      connection,
      config.UNCLAIMED_UPLOAD_TTL_HOURS,
      config.UNCLAIMED_UPLOAD_CLEANUP_BATCH - retries.length
    );
    const unfilled = config.UNCLAIMED_UPLOAD_CLEANUP_BATCH - retries.length - stale.length;
    const extraRetries = await retryCandidates(connection, unfilled, retries.length);
    const candidates = [...new Set([...retries, ...stale, ...extraRetries])];

    for (const cosKey of candidates) {
      try {
        // The committed `deleting` tombstone makes the key unclaimable before
        // this external side effect. A crash or DB failure leaves the tombstone
        // for an idempotent retry instead of restoring an invalid upload row.
        await storage.delete(cosKey);
        const [deleted] = await connection.execute<ResultSetHeader>(
          "DELETE FROM proof_files WHERE cos_key = ? AND owner_type = 'deleting'",
          [cosKey]
        );
        if (deleted.affectedRows !== 1) throw new Error("Deletion tombstone disappeared before finalization");
        removed += 1;
      } catch (error) {
        const message = error instanceof Error ? error.message : "unknown deletion error";
        failures.push({ cosKey, message });
        try {
          await markFailure(connection, cosKey);
        } catch (markError) {
          const markMessage = markError instanceof Error ? markError.message : "unknown database error";
          failures.push({ cosKey, message: `could not record cleanup failure: ${markMessage}` });
        }
      }
    }
  } finally {
    if (connection) {
      if (locked && cleanupLock) {
        await connection.execute("SELECT RELEASE_LOCK(?)", [cleanupLock]).catch(() => undefined);
      }
      connection.release();
    }
    await pool.end();
  }

  process.stdout.write(`Removed ${removed} stale unclaimed proof object(s).\n`);
  if (failures.length > 0) {
    for (const failure of failures) {
      process.stderr.write(`Failed to clean ${failure.cosKey}: ${failure.message}\n`);
    }
    throw new Error(`${failures.length} proof cleanup operation(s) failed`);
  }
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "unknown cleanup error";
  process.stderr.write(`Proof cleanup failed: ${message}\n`);
  process.exitCode = 1;
});
