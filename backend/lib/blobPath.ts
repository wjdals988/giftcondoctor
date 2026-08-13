import { ApiError } from "./http";

const SEGMENT_PATTERN = /^[A-Za-z0-9_-]+$/;

export function couponBlobPrefix(roomId: string, couponId: string): string {
  if (!SEGMENT_PATTERN.test(roomId) || !SEGMENT_PATTERN.test(couponId)) {
    throw new ApiError(400, "roomId 또는 couponId 형식이 올바르지 않습니다.");
  }
  return `rooms/${roomId}/coupons/${couponId}/`;
}

export function requireCouponBlobPath(path: unknown, roomId: string, couponId: string): string {
  const prefix = couponBlobPrefix(roomId, couponId);
  if (typeof path !== "string" || !path.startsWith(prefix) || path.length <= prefix.length) {
    throw new ApiError(409, "쿠폰 이미지 경로가 쿠폰 정보와 일치하지 않습니다.");
  }
  return path;
}
