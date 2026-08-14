import { randomUUID } from "crypto";
import { del, put } from "@vercel/blob";
import { requireRoomMember, requireUser } from "@/lib/auth";
import { couponBlobPrefix, requireCouponBlobPath } from "@/lib/blobPath";
import { ApiError, json, jsonError } from "@/lib/http";
import { detectSupportedImage, MAX_IMAGE_SIZE } from "@/lib/imageUpload";
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
    const path = `${couponBlobPrefix(roomId, couponId)}${randomUUID()}.${detected.extension}`;
    const blob = await put(path, buffer, {
      access: "private",
      contentType: detected.contentType
    });

    return json({
      blobPath: blob.pathname ?? path,
      imageWidth: null,
      imageHeight: null,
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
    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");

    await requireRoomMember(roomId, token.uid);
    await del(requireCouponBlobPath(blobPath, roomId, couponId));
    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
