import { randomUUID } from "crypto";
import { del, put } from "@vercel/blob";
import { requireRoomMember, requireUser } from "@/lib/auth";
import { couponBlobPrefix, requireCouponBlobPath } from "@/lib/blobPath";
import { ApiError, json, jsonError } from "@/lib/http";
import { detectSupportedImage, MAX_IMAGE_SIZE } from "@/lib/imageUpload";
import { createCouponThumbnail } from "@/lib/imageThumbnail";
import { enforceUserRateLimit } from "@/lib/rateLimit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "coupon-image-upload", limit: 20, windowSeconds: 3600 });
    const form = await request.formData();
    const roomId = String(form.get("roomId") ?? "").trim();
    const couponId = String(form.get("couponId") ?? "").trim();
    const image = form.get("image");

    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }
    if (!(image instanceof File)) {
      throw new ApiError(400, "이미지 파일이 필요합니다.");
    }
    if (image.size === 0) throw new ApiError(400, "빈 이미지는 업로드할 수 없습니다.");
    if (image.size > MAX_IMAGE_SIZE) {
      throw new ApiError(413, "이미지는 최대 10MB까지 업로드할 수 있습니다.");
    }

    await requireRoomMember(roomId, token.uid);

    const buffer = Buffer.from(await image.arrayBuffer());
    const detected = detectSupportedImage(buffer);
    const prefix = couponBlobPrefix(roomId, couponId);
    const path = `${prefix}${randomUUID()}.${detected.extension}`;
    const thumbnailPath = `${prefix}${randomUUID()}-thumbnail.webp`;
    const thumbnail = await createCouponThumbnail(buffer);

    const [blobResult, thumbnailBlobResult] = await Promise.allSettled([
      put(path, buffer, {
        access: "private",
        contentType: detected.contentType
      }),
      put(thumbnailPath, thumbnail.data, {
        access: "private",
        contentType: "image/webp"
      })
    ]);
    if (blobResult.status === "rejected" || thumbnailBlobResult.status === "rejected") {
      const uploadedPaths = [
        blobResult.status === "fulfilled" ? blobResult.value.pathname ?? path : null,
        thumbnailBlobResult.status === "fulfilled" ? thumbnailBlobResult.value.pathname ?? thumbnailPath : null
      ].filter((uploadedPath): uploadedPath is string => uploadedPath !== null);
      if (uploadedPaths.length > 0) await del(uploadedPaths).catch(() => undefined);
      if (blobResult.status === "rejected") throw blobResult.reason;
      if (thumbnailBlobResult.status === "rejected") throw thumbnailBlobResult.reason;
      throw new Error("쿠폰 이미지 업로드 상태가 올바르지 않습니다.");
    }
    const blob = blobResult.value;
    const thumbnailBlob = thumbnailBlobResult.value;

    return json({
      blobPath: blob.pathname ?? path,
      thumbnailBlobPath: thumbnailBlob.pathname ?? thumbnailPath,
      thumbnailSize: thumbnail.data.byteLength,
      thumbnailWidth: thumbnail.width,
      thumbnailHeight: thumbnail.height,
      imageWidth: thumbnail.sourceWidth,
      imageHeight: thumbnail.sourceHeight,
      contentType: detected.contentType,
      size: image.size
    });
  } catch (error) {
    return jsonError(error);
  }
}

export async function DELETE(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();
    const blobPath = url.searchParams.get("blobPath")?.trim();
    const thumbnailBlobPath = url.searchParams.get("thumbnailBlobPath")?.trim();
    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");

    await requireRoomMember(roomId, token.uid);
    const paths = [requireCouponBlobPath(blobPath, roomId, couponId)];
    if (thumbnailBlobPath) {
      paths.push(requireCouponBlobPath(thumbnailBlobPath, roomId, couponId));
    }
    await del(paths);
    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
