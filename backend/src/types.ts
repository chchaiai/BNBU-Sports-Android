export type Role = "student" | "teacher" | "admin";
export type Gender = "male" | "female";
export type GradeLevel = "freshman" | "sophomore" | "junior" | "senior";

export interface AuthenticatedUser {
  id: string;
  role: Role;
  tokenVersion: number;
}

export interface UserRecord {
  id: string;
  studentNumber: string;
  email: string;
  passwordHash: string;
  role: Role;
  name: string;
  college: string;
  className: string;
  gender: Gender | null;
  preferredLanguage: "zh-CN" | "en";
  gradeLevel: GradeLevel | null;
  admissionYear: number | null;
  status: string;
  tokenVersion: number;
}

export type ContactChannel = "email" | "phone";

export interface ContactBindingStatus {
  accountStatus: "PENDING_CONTACT_BINDING" | "ACTIVE";
  contacts: {
    email: { masked: string | null; verified: boolean };
    phone: { masked: string | null; verified: boolean };
  };
}

export interface ProofFile {
  url: string;
  cosKey: string;
  mediaType: "image" | "video";
  mimeType: string;
  size: number;
}

/** Opaque device address used exclusively by the FCM delivery service. */
export interface PushDeviceRegistration {
  token: string;
  platform: "android";
  appVersion: string;
}

/** Server-managed help content. Only published records are visible to students. */
export interface HelpArticleInput {
  title: string;
  category: string;
  content: string;
  sortOrder: number;
  status: "draft" | "published" | "offline";
}

export interface StoredUpload extends ProofFile {
  studentId: string;
}

export interface CreateRecordInput {
  creditType: "课程相关" | "其他运动";
  courseId: string | null;
  taskId: string | null;
  hours: number;
  description: string;
  remark: string;
  proofFiles: Array<Omit<ProofFile, "url"> & { url?: string }>;
  sportType: string | null;
  startTime: string;
  endTime: string;
  actualDurationSeconds: number;
  idempotencyKey?: string;
  idempotencyHash?: string;
}

export interface CreateExemptionInput {
  type: "800m" | "1000m" | "team" | "club";
  reason: string;
  proofFiles: string[];
  organization: string | null;
  idempotencyKey?: string;
  idempotencyHash?: string;
}

export interface SupplementExemptionInput {
  reason: string;
  proofFiles: string[];
  organization: string | null;
  idempotencyKey?: string;
  idempotencyHash?: string;
}

/** A student-reported problem. Contact details are ticket-scoped. */
export interface CreateFeedbackInput {
  category: string;
  description: string;
  currentPage: string;
  clientVersion: string;
  screenshots: string[];
  email: string;
  phone: string;
  idempotencyKey?: string;
  idempotencyHash?: string;
}

export interface UploadedFileInput {
  originalName: string;
  mimeType: string;
  mediaType: "image" | "video";
  size: number;
  buffer: Buffer;
}
