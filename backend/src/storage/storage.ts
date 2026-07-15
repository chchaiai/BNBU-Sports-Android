import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import type { AppConfig } from "../config/env";
import type { ProofFile, UploadedFileInput } from "../types";

export interface ObjectStorage {
  health(): Promise<boolean>;
  put(studentId: string, file: UploadedFileInput, cosKey?: string): Promise<ProofFile>;
  url(cosKey: string): Promise<string>;
  delete(cosKey: string): Promise<void>;
}

const extensionByMime: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
  "image/heic": "heic",
  "image/heif": "heif",
  "video/mp4": "mp4",
  "video/quicktime": "mov"
};

export function createProofObjectKey(studentId: string, mimeType: string): string {
  const extension = extensionByMime[mimeType] ?? "bin";
  const date = new Date().toISOString().slice(0, 10).replaceAll("-", "/");
  return `proofs/${studentId}/${date}/${randomUUID()}.${extension}`;
}

export class LocalObjectStorage implements ObjectStorage {
  private readonly root: string;

  constructor(private readonly config: AppConfig) {
    this.root = path.resolve(config.UPLOAD_LOCAL_DIR);
  }

  async health(): Promise<boolean> {
    await mkdir(this.root, { recursive: true });
    return true;
  }

  async put(studentId: string, file: UploadedFileInput, plannedCosKey?: string): Promise<ProofFile> {
    const cosKey = plannedCosKey ?? createProofObjectKey(studentId, file.mimeType);
    if (!cosKey.startsWith(`proofs/${studentId}/`)) throw new Error("Invalid proof object key");
    const target = path.join(this.root, ...cosKey.split("/"));
    await mkdir(path.dirname(target), { recursive: true });
    await writeFile(target, file.buffer, { flag: "wx" });
    return {
      url: await this.url(cosKey),
      cosKey,
      mediaType: file.mediaType,
      mimeType: file.mimeType,
      size: file.size
    };
  }

  async url(cosKey: string): Promise<string> {
    return `${this.config.PUBLIC_BASE_URL.replace(/\/$/, "")}/uploads/${cosKey.split("/").map(encodeURIComponent).join("/")}`;
  }

  async delete(cosKey: string): Promise<void> {
    const target = path.resolve(this.root, ...cosKey.split("/"));
    if (!target.startsWith(`${this.root}${path.sep}`)) return;
    await rm(target, { force: true });
  }

  async read(cosKey: string): Promise<Buffer> {
    return readFile(path.join(this.root, ...cosKey.split("/")));
  }
}

type CosSdk = {
  putObject(parameters: Record<string, unknown>, callback: (error: Error | null) => void): void;
  deleteObject(parameters: Record<string, unknown>, callback: (error: Error | null) => void): void;
  headBucket(parameters: Record<string, unknown>, callback: (error: Error | null) => void): void;
  getObjectUrl(parameters: Record<string, unknown>, callback: (error: Error | null, data?: { Url?: string }) => void): void;
};

export class CosObjectStorage implements ObjectStorage {
  private readonly client: CosSdk;

  constructor(private readonly config: AppConfig) {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const COS = require("cos-nodejs-sdk-v5") as new (options: Record<string, string>) => CosSdk;
    this.client = new COS({ SecretId: config.COS_SECRET_ID, SecretKey: config.COS_SECRET_KEY });
  }

  async health(): Promise<boolean> {
    try {
      await this.call("headBucket", { Bucket: this.config.COS_BUCKET, Region: this.config.COS_REGION });
      return true;
    } catch {
      return false;
    }
  }

  async put(studentId: string, file: UploadedFileInput, plannedCosKey?: string): Promise<ProofFile> {
    const cosKey = plannedCosKey ?? createProofObjectKey(studentId, file.mimeType);
    if (!cosKey.startsWith(`proofs/${studentId}/`)) throw new Error("Invalid proof object key");
    await this.call("putObject", {
      Bucket: this.config.COS_BUCKET,
      Region: this.config.COS_REGION,
      Key: cosKey,
      Body: file.buffer,
      ContentType: file.mimeType,
      ContentLength: file.size
    });
    try {
      const url = await this.url(cosKey);
      return { url, cosKey, mediaType: file.mediaType, mimeType: file.mimeType, size: file.size };
    } catch (error) {
      await this.delete(cosKey).catch(() => undefined);
      throw error;
    }
  }

  async url(cosKey: string): Promise<string> {
    return new Promise<string>((resolve, reject) => {
      this.client.getObjectUrl(
        {
          Bucket: this.config.COS_BUCKET,
          Region: this.config.COS_REGION,
          Key: cosKey,
          Sign: true,
          Expires: this.config.COS_SIGNED_URL_TTL_SECONDS
        },
        (error, data) => {
          if (error) {
            reject(error);
          } else if (!data?.Url) {
            reject(new Error("COS did not return a signed object URL"));
          } else {
            resolve(data.Url);
          }
        }
      );
    });
  }

  async delete(cosKey: string): Promise<void> {
    await this.call("deleteObject", {
      Bucket: this.config.COS_BUCKET,
      Region: this.config.COS_REGION,
      Key: cosKey
    });
  }

  private call(method: "putObject" | "deleteObject" | "headBucket", parameters: Record<string, unknown>): Promise<void> {
    return new Promise((resolve, reject) => {
      this.client[method](parameters, (error) => error ? reject(error) : resolve());
    });
  }
}

export function createStorage(config: AppConfig): ObjectStorage {
  return config.STORAGE_DRIVER === "cos" ? new CosObjectStorage(config) : new LocalObjectStorage(config);
}
