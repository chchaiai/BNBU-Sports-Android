import type {
  CreateExemptionInput,
  CreateRecordInput,
  ProofFile,
  SupplementRecordInput,
  SupplementExemptionInput,
  UserRecord
} from "./types";

export type JsonObject = Record<string, any>;

export interface BackendStore {
  ping(): Promise<boolean>;
  close(): Promise<void>;
  findUserByAccount(account: string): Promise<UserRecord | null>;
  findUserById(id: string): Promise<UserRecord | null>;
  getProfile(studentId: string): Promise<JsonObject>;
  updateProfile(studentId: string, gender: "male" | "female" | null): Promise<JsonObject>;
  getSportSummary(studentId: string): Promise<JsonObject>;
  listSportRecords(studentId: string, filters?: { status?: string; courseId?: string; limit?: number; offset?: number }): Promise<JsonObject[]>;
  getSportRecord(studentId: string, recordId: string): Promise<JsonObject | null>;
  createSportRecord(studentId: string, input: CreateRecordInput): Promise<JsonObject>;
  supplementSportRecord(studentId: string, recordId: string, input: SupplementRecordInput): Promise<JsonObject>;
  listIdentities(studentId: string): Promise<JsonObject[]>;
  listNotifications(studentId: string): Promise<JsonObject[]>;
  markNotificationRead(studentId: string, notificationId: string): Promise<JsonObject | null>;
  listCourses(studentId: string, scope: "all" | "current" | "history", semesterId?: string): Promise<JsonObject>;
  listTasks(studentId: string): Promise<JsonObject>;
  getGrades(studentId: string): Promise<JsonObject>;
  listExemptions(studentId: string, category?: "physical_test" | "checkin", page?: { limit?: number; offset?: number }): Promise<JsonObject[]>;
  createExemption(studentId: string, category: "physical_test" | "checkin", input: CreateExemptionInput): Promise<JsonObject>;
  supplementExemption(studentId: string, exemptionId: string, category: "physical_test" | "checkin", input: SupplementExemptionInput): Promise<JsonObject>;
  convertEndurance(timeSeconds: number, gender: string, gradeLevel: string): Promise<JsonObject | null>;
  registerProofFiles(studentId: string, proofs: ProofFile[]): Promise<void>;
}
