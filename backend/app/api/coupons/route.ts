import { requireUser } from "@/lib/auth";
import { softDeleteCoupon } from "@/lib/couponTrashStore";
import { ApiError, json, jsonError } from "@/lib/http";

export const runtime = "nodejs";

export async function DELETE(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();

    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }

    const deletedCoupon = await softDeleteCoupon(roomId, couponId, token.uid);
    return json({ ok: true, deletedCoupon });
  } catch (error) {
    return jsonError(error);
  }
}
