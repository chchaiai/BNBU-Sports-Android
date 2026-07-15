import { AppError } from "../errors";
import type { UploadedFileInput } from "../types";

const IMAGE_LIMIT = 8_000_000;
const VIDEO_LIMIT = 100_000_000;

function ascii(buffer: Buffer, start: number, end: number): string {
  return buffer.subarray(start, end).toString("ascii");
}

function sniffMime(buffer: Buffer): string | null {
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) return "image/jpeg";
  if (buffer.length >= 8 && buffer.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return "image/png";
  if (buffer.length >= 12 && ascii(buffer, 0, 4) === "RIFF" && ascii(buffer, 8, 12) === "WEBP") return "image/webp";
  if (buffer.length >= 12 && ascii(buffer, 4, 8) === "ftyp") {
    const brand = ascii(buffer, 8, 12).toLowerCase();
    if (["heic", "heix", "hevc", "hevx", "mif1", "msf1"].includes(brand)) return brand.startsWith("hei") ? "image/heic" : "image/heif";
    if (["qt  "].includes(brand)) return "video/quicktime";
    return "video/mp4";
  }
  return null;
}

export function validateUploads(files: Express.Multer.File[], maxRequestBytes = 120_000_000): UploadedFileInput[] {
  if (files.length === 0) throw new AppError(422, "PROOF_REQUIRED", "至少需要上传 1 个凭证");
  if (files.length > 7) throw new AppError(413, "TOO_MANY_FILES", "最多上传 6 张图片和 1 个视频");

  const converted = files.map((file) => {
    const detected = sniffMime(file.buffer);
    if (!detected) throw new AppError(415, "UNSUPPORTED_MEDIA", `无法识别文件内容: ${file.originalname}`);
    const mediaType = detected.startsWith("video/") ? "video" as const : "image" as const;
    if (mediaType === "image" && file.size > IMAGE_LIMIT) throw new AppError(413, "IMAGE_TOO_LARGE", "单张图片不能超过 8MB");
    if (mediaType === "video" && file.size > VIDEO_LIMIT) throw new AppError(413, "VIDEO_TOO_LARGE", "视频不能超过 100MB");
    return {
      originalName: file.originalname,
      mimeType: detected,
      mediaType,
      size: file.size,
      buffer: file.buffer
    };
  });

  if (converted.reduce((total, file) => total + file.size, 0) > maxRequestBytes) {
    throw new AppError(413, "REQUEST_TOO_LARGE", "单次上传文件总大小不能超过 120MB");
  }

  if (converted.filter((file) => file.mediaType === "image").length > 6) throw new AppError(413, "TOO_MANY_IMAGES", "最多上传 6 张图片");
  if (converted.filter((file) => file.mediaType === "video").length > 1) throw new AppError(413, "TOO_MANY_VIDEOS", "最多上传 1 个视频");
  return converted;
}
