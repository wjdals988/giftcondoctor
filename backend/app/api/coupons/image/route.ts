import { get } from "@vercel/blob";
import { requireCouponAccess, requireUser } from "@/lib/auth";
import { ApiError, jsonError } from "@/lib/http";

export const runtime = "nodejs";

type PrivateBlob = {
  body?: BodyInit | null;
  contentType?: string;
  size?: number;
};

export async function GET(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();

    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }

    const coupon = await requireCouponAccess(roomId, couponId, token.uid);
    const blobPath = coupon.get("imageBlobPath");
    if (!blobPath || typeof blobPath !== "string") {
      throw new ApiError(404, "쿠폰 이미지가 없습니다.");
    }

    const privateBlob = (await get(blobPath, { access: "private" })) as PrivateBlob;
    if (!privateBlob.body) {
      throw new ApiError(404, "쿠폰 이미지를 찾을 수 없습니다.");
    }

    return new Response(privateBlob.body, {
      headers: {
        "Content-Type": privateBlob.contentType ?? "application/octet-stream",
        "Cache-Control": "private, no-store",
        "X-Content-Type-Options": "nosniff"
      }
    });
  } catch (error) {
    return jsonError(error);
  }
}
