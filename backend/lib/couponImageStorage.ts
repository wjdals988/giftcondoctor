import { randomUUID } from "crypto";
import { del, put } from "@vercel/blob";
import { couponBlobPrefix } from "./blobPath";
import { detectSupportedImage, MAX_IMAGE_SIZE } from "./imageUpload";
import { createCouponThumbnail } from "./imageThumbnail";
import { ApiError } from "./http";

export type StoredCouponImage = {
  uploadId: string;
  blobPath: string;
  thumbnailBlobPath: string;
  thumbnailSize: number;
  thumbnailWidth: number;
  thumbnailHeight: number;
  imageWidth: number | null;
  imageHeight: number | null;
  contentType: string;
  size: number;
};

type CouponImageStorageDependencies = {
  putImage: (
    path: string,
    data: Buffer,
    options: { access: "private"; contentType: string; addRandomSuffix: false }
  ) => Promise<{ pathname?: string }>;
  createThumbnail: typeof createCouponThumbnail;
  deleteImages: (paths: string[]) => Promise<unknown>;
};

type CouponImageStorageOptions = {
  uploadId?: string;
  dependencies?: CouponImageStorageDependencies;
};

const defaultDependencies: CouponImageStorageDependencies = {
  putImage: (path, data, options) => put(path, data, options),
  createThumbnail: createCouponThumbnail,
  deleteImages: (paths) => del(paths)
};

const UPLOAD_ID_PATTERN = /^[A-Za-z0-9_-]{16,64}$/;

export function requireCouponUploadId(value: unknown): string {
  if (typeof value !== "string" || !UPLOAD_ID_PATTERN.test(value)) {
    throw new ApiError(400, "uploadId 형식이 올바르지 않습니다.");
  }
  return value;
}

export function couponUploadCandidatePaths(roomId: string, couponId: string, uploadId: string) {
  const validatedUploadId = requireCouponUploadId(uploadId);
  const prefix = couponBlobPrefix(roomId, couponId);
  return [
    `${prefix}${validatedUploadId}-original.jpg`,
    `${prefix}${validatedUploadId}-original.png`,
    `${prefix}${validatedUploadId}-original.webp`,
    `${prefix}${validatedUploadId}-thumbnail.webp`
  ];
}

export async function storeCouponImage(
  roomId: string,
  couponId: string,
  image: File,
  options: CouponImageStorageOptions = {}
): Promise<StoredCouponImage> {
  if (image.size === 0) throw new ApiError(400, "빈 이미지는 업로드할 수 없습니다.");
  if (image.size > MAX_IMAGE_SIZE) {
    throw new ApiError(413, "이미지는 최대 10MB까지 업로드할 수 있습니다.");
  }

  const buffer = Buffer.from(await image.arrayBuffer());
  const detected = detectSupportedImage(buffer);
  const prefix = couponBlobPrefix(roomId, couponId);
  const uploadId = requireCouponUploadId(options.uploadId ?? randomUUID());
  const dependencies = options.dependencies ?? defaultDependencies;
  const path = `${prefix}${uploadId}-original.${detected.extension}`;
  const thumbnailPath = `${prefix}${uploadId}-thumbnail.webp`;

  const [blobResult, thumbnailBlobResult] = await Promise.allSettled([
    dependencies.putImage(path, buffer, {
      access: "private",
      contentType: detected.contentType,
      addRandomSuffix: false
    }),
    dependencies.createThumbnail(buffer).then(async (thumbnail) => ({
      thumbnail,
      blob: await dependencies.putImage(thumbnailPath, thumbnail.data, {
        access: "private",
        contentType: "image/webp",
        addRandomSuffix: false
      })
    }))
  ]);

  if (blobResult.status === "rejected" || thumbnailBlobResult.status === "rejected") {
    const uploadedPaths = [
      blobResult.status === "fulfilled" ? blobResult.value.pathname ?? path : null,
      thumbnailBlobResult.status === "fulfilled"
        ? thumbnailBlobResult.value.blob.pathname ?? thumbnailPath
        : null
    ].filter((uploadedPath): uploadedPath is string => uploadedPath !== null);
    if (uploadedPaths.length > 0) await dependencies.deleteImages(uploadedPaths).catch(() => undefined);
    if (blobResult.status === "rejected") throw blobResult.reason;
    if (thumbnailBlobResult.status === "rejected") throw thumbnailBlobResult.reason;
    throw new Error("쿠폰 이미지 업로드 상태가 올바르지 않습니다.");
  }

  const thumbnail = thumbnailBlobResult.value.thumbnail;
  const thumbnailBlob = thumbnailBlobResult.value.blob;

  return {
    uploadId,
    blobPath: blobResult.value.pathname ?? path,
    thumbnailBlobPath: thumbnailBlob.pathname ?? thumbnailPath,
    thumbnailSize: thumbnail.data.byteLength,
    thumbnailWidth: thumbnail.width,
    thumbnailHeight: thumbnail.height,
    imageWidth: thumbnail.sourceWidth,
    imageHeight: thumbnail.sourceHeight,
    contentType: detected.contentType,
    size: image.size
  };
}

export async function deleteCouponImages(paths: string[]): Promise<void> {
  if (paths.length > 0) await del(paths);
}
