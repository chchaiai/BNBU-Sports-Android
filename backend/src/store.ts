import type {
  ContactBindingStatus,
  ContactChannel,
  CreateFeedbackInput,
  CreateExemptionInput,
  CreateRecordInput,
  ProofFile,
  SupplementExemptionInput,
  UserRecord
} from "./types";

export type JsonObject = Record<string, any>;

export interface BackendStore {
  ping(): Promise<boolean>;
  close(): Promise<void>;
  findUserByAccount(account: string): Promise<UserRecord | null>;
  findUserById(id: string): Promise<UserRecord | null>;
  getContactBindingStatus(studentId: string): Promise<ContactBindingStatus>;
  issueContactVerificationCode(studentId: string, channel: ContactChannel, contact: string, codeHash: string, expiresAt: Date): Promise<void>;
  verifyContactVerificationCode(studentId: string, channel: ContactChannel, contact: string, codeHash: string): Promise<JsonObject>;
  getProfile(studentId: string): Promise<JsonObject>;
  updateProfile(studentId: string, gender: "male" | "female" | null): Promise<JsonObject>;
  updateLanguagePreference(studentId: string, language: "zh-CN" | "en"): Promise<JsonObject>;
  getSportSummary(studentId: string): Promise<JsonObject>;
  listSportRecords(studentId: string, filters?: { courseId?: string; limit?: number; offset?: number }): Promise<JsonObject[]>;
  getSportRecord(studentId: string, recordId: string): Promise<JsonObject | null>;
  createSportRecord(studentId: string, input: CreateRecordInput): Promise<JsonObject>;
  listIdentities(studentId: string): Promise<JsonObject[]>;
  listNotifications(studentId: string): Promise<JsonObject[]>;
  markNotificationRead(studentId: string, notificationId: string): Promise<JsonObject | null>;
  registerPushDevice(studentId: string, input: import("./types").PushDeviceRegistration): Promise<void>;
  unregisterPushDevice(studentId: string, token: string): Promise<void>;
  listCourses(studentId: string, scope: "all" | "current" | "history", semesterId?: string): Promise<JsonObject>;
  getCheckInTimeWindow(studentId: string): Promise<JsonObject>;
  listTasks(studentId: string): Promise<JsonObject>;
  getGrades(studentId: string): Promise<JsonObject>;
  listExemptions(studentId: string, category?: "physical_test" | "checkin", page?: { limit?: number; offset?: number }): Promise<JsonObject[]>;
  createExemption(studentId: string, category: "physical_test" | "checkin", input: CreateExemptionInput): Promise<JsonObject>;
  supplementExemption(studentId: string, exemptionId: string, category: "physical_test" | "checkin", input: SupplementExemptionInput): Promise<JsonObject>;
  createFeedback(studentId: string, input: CreateFeedbackInput): Promise<JsonObject>;
  listFeedbackTickets(studentId: string): Promise<JsonObject[]>;
  convertEndurance(timeSeconds: number, gender: string, gradeLevel: string): Promise<JsonObject | null>;
  listPublishedHelpArticles(): Promise<JsonObject[]>;
  listHelpArticles(): Promise<JsonObject[]>;
  upsertHelpArticle(adminId: string, articleId: string | null, input: import("./types").HelpArticleInput): Promise<JsonObject>;
  registerProofFiles(studentId: string, proofs: ProofFile[]): Promise<void>;
}
