import { requireUser } from "@/lib/auth";
import {
  claimDeletedCouponForPurge,
  listDeletedCoupons,
  purgeCouponDocument,
  restoreDeletedCoupon
} from "@/lib/couponTrashStore";
import { decodeCouponTrashCursor } from "@/lib/couponTrash";
import { ApiError, json, jsonError, readJson } from "@/lib/http";

export const runtime = "nodejs";

export async function GET(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    if (!roomId) throw new ApiError(400, "roomId가 필요합니다.");
    const cursorValue = url.searchParams.get("cursor")?.trim();
    const cursor = cursorValue ? decodeCouponTrashCursor(cursorValue) : null;
    if (cursorValue && !cursor) throw new ApiError(400, "cursor가 올바르지 않습니다.");
    return json(await listDeletedCoupons(roomId, token.uid, cursor));
  } catch (error) {
    return jsonError(error);
  }
}

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Record<string, unknown>>(request);
    const roomId = stringField(body.roomId, "roomId가 필요합니다.");
    const couponId = stringField(body.couponId, "couponId가 필요합니다.");
    await restoreDeletedCoupon(roomId, couponId, token.uid);
    return json({ ok: true });
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
    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    const ref = await claimDeletedCouponForPurge(roomId, couponId, token.uid);
    const coupon = await ref.get();
    const result = await purgeCouponDocument(coupon);
    return json({ ok: true, ...result });
  } catch (error) {
    return jsonError(error);
  }
}

function stringField(value: unknown, message: string): string {
  if (typeof value !== "string" || value.trim().length === 0) throw new ApiError(400, message);
  return value.trim();
}
