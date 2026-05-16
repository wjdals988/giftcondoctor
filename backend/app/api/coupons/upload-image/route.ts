import { randomUUID } from "crypto";
import { put } from "@vercel/blob";
import { imageSize } from "image-size";
import { requireRoomMember, requireUser } from "@/lib/auth";
import { ApiError, json, jsonError } from "@/lib/http";

export const runtime = "nodejs";

const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

function safeFileName(name: string) {
  return name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 80) || "coupon-image";
}

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
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
    if (!image.type.startsWith("image/")) {
      throw new ApiError(400, "이미지 파일만 업로드할 수 있습니다.");
    }
    if (image.size > MAX_IMAGE_SIZE) {
      throw new ApiError(413, "이미지는 최대 10MB까지 업로드할 수 있습니다.");
    }

    await requireRoomMember(roomId, token.uid);

    const buffer = Buffer.from(await image.arrayBuffer());
    const dimensions = imageSize(buffer);
    const path = `rooms/${roomId}/coupons/${couponId}/${randomUUID()}-${safeFileName(image.name)}`;
    const blob = await put(path, buffer, {
      access: "private",
      contentType: image.type
    });

    return json({
      blobPath: blob.pathname ?? path,
      imageWidth: dimensions.width ?? null,
      imageHeight: dimensions.height ?? null,
      contentType: image.type,
      size: image.size
    });
  } catch (error) {
    return jsonError(error);
  }
}
