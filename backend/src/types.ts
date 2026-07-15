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
  gradeLevel: GradeLevel | null;
  admissionYear: number | null;
  status: string;
  tokenVersion: number;
}

export interface ProofFile {
  url: string;
  cosKey: string;
  mediaType: "image" | "video";
  mimeType: string;
  size: number;
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
  proofFiles: Array<Omit<ProofFile, "url"> & { url?: string }>;
  sportType: string | null;
  idempotencyKey?: string;
  idempotencyHash?: string;
}

export interface SupplementRecordInput {
  hours: number;
  description: string;
  proofFiles: Array<Omit<ProofFile, "url"> & { url?: string }>;
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

export interface UploadedFileInput {
  originalName: string;
  mimeType: string;
  mediaType: "image" | "video";
  size: number;
  buffer: Buffer;
}
