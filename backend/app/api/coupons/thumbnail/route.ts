import { del, get, put } from "@vercel/blob";
import { requireCouponAccess, requireUser } from "@/lib/auth";
import { requireCouponBlobPath } from "@/lib/blobPath";
import { ApiError, jsonError } from "@/lib/http";
import { MAX_IMAGE_SIZE } from "@/lib/imageUpload";
import { createCouponThumbnail, legacyCouponThumbnailPath } from "@/lib/imageThumbnail";
import { enforceUserRateLimit } from "@/lib/rateLimit";

export const runtime = "nodejs";

function thumbnailResponse(
  body: BodyInit,
  size: number,
  backfilled: boolean,
  contentType = "image/webp"
): Response {
  return new Response(body, {
    headers: {
      "Content-Type": contentType,
      "Content-Length": String(size),
      "Cache-Control": "private, max-age=3600",
      "X-Content-Type-Options": "nosniff",
      "X-Thumbnail-Backfilled": backfilled ? "1" : "0"
    }
  });
}

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();
    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }

    const coupon = await requireCouponAccess(roomId, couponId, token.uid);
    const existingPath = coupon.get("thumbnailBlobPath");
    if (existingPath) {
      const blobPath = requireCouponBlobPath(existingPath, roomId, couponId);
      const existing = await get(blobPath, { access: "private", useCache: false });
      if (!existing || existing.statusCode !== 200 || !existing.stream) {
        throw new ApiError(404, "쿠폰 썸네일을 찾을 수 없습니다.");
      }
      return thumbnailResponse(
        existing.stream,
        existing.blob.size,
        false,
        existing.blob.contentType ?? "application/octet-stream"
      );
    }

    await enforceUserRateLimit(token.uid, {
      action: "coupon-thumbnail-backfill",
      limit: 100,
      windowSeconds: 3600
    });

    const originalPath = requireCouponBlobPath(coupon.get("imageBlobPath"), roomId, couponId);
    const original = await get(originalPath, { access: "private", useCache: false });
    if (!original || original.statusCode !== 200 || !original.stream) {
      throw new ApiError(404, "쿠폰 이미지를 찾을 수 없습니다.");
    }
    if (original.blob.size > MAX_IMAGE_SIZE) {
      throw new ApiError(413, "기존 쿠폰 이미지가 썸네일 변환 제한을 초과합니다.");
    }
    const originalBuffer = Buffer.from(await new Response(original.stream).arrayBuffer());
    if (originalBuffer.byteLength > MAX_IMAGE_SIZE) {
      throw new ApiError(413, "기존 쿠폰 이미지가 썸네일 변환 제한을 초과합니다.");
    }

    const thumbnail = await createCouponThumbnail(originalBuffer);
    const thumbnailPath = legacyCouponThumbnailPath(roomId, couponId);
    const uploaded = await put(thumbnailPath, thumbnail.data, {
      access: "private",
      allowOverwrite: true,
      cacheControlMaxAge: 3600,
      contentType: "image/webp"
    });

    try {
      await coupon.ref.update({ thumbnailBlobPath: uploaded.pathname ?? thumbnailPath });
    } catch (error) {
      const latest = await coupon.ref.get().catch(() => null);
      if (!latest?.exists || latest.get("thumbnailBlobPath") !== (uploaded.pathname ?? thumbnailPath)) {
        await del(uploaded.pathname ?? thumbnailPath).catch(() => undefined);
      }
      throw error;
    }

    return thumbnailResponse(new Uint8Array(thumbnail.data), thumbnail.data.byteLength, true);
  } catch (error) {
    return jsonError(error);
  }
}
