import { readFile } from "node:fs/promises";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("OpenAPI contract", () => {
  it("documents every Android endpoint and both exemption supplement routes", async () => {
    const specification = await readFile(path.resolve(process.cwd(), "openapi/openapi.yaml"), "utf8");
    for (const route of [
      "/auth/login:", "/sport/summary:", "/sport/records:", "/sport/records/{id}:",
      "/sport/records/{id}/supplements:", "/sport/identity:", "/common/notifications:",
      "/common/notifications/{id}/read:", "/student/profile:", "/student/courses:",
      "/student/tasks:", "/student/grades:", "/student/physical-test-exemptions:",
      "/student/physical-test-exemptions/{id}/supplements:", "/student/checkin-exemptions:",
      "/student/checkin-exemptions/{id}/supplements:", "/scoring/convert-endurance:", "/upload/proof:"
    ]) expect(specification).toContain(route);
    expect(specification).toContain("IDEMPOTENCY_KEY_REUSED");
    expect(specification).toContain("#/components/requestBodies/PhysicalExemption");
    expect(specification).toContain("#/components/requestBodies/CheckinExemption");
  });

  it("keeps task completion and idempotency persistence safety in the SQL contract", async () => {
    const storeSource = await readFile(path.resolve(process.cwd(), "src/db/mysql-store.ts"), "utf8");
    const taskFilter = /WHERE task_id IS NOT NULL\s+AND status IN \(([^)]+)\)/.exec(storeSource)?.[1] ?? "";
    expect(taskFilter).toContain("\u5f85\u5ba1\u6838");
    expect(taskFilter).toContain("\u5df2\u901a\u8fc7");
    expect(taskFilter).not.toContain("\u5df2\u9a73\u56de");
    expect(taskFilter).not.toContain("\u8865\u6750\u6599");

    const migration = await readFile(path.resolve(process.cwd(), "db/migrations/001_initial.sql"), "utf8");
    expect(migration).toContain("request_hash CHAR(64) NOT NULL");
  });
});
