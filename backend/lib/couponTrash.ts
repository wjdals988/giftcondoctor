export const COUPON_TRASH_RETENTION_DAYS = 30;
export const COUPON_TRASH_RETENTION_MS = COUPON_TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000;

const RESTORABLE_STATUSES = new Set(["active", "reserved", "used", "expired"]);
const RESTORABLE_VISIBILITIES = new Set(["room", "private"]);

export type CouponTrashState = {
  status: string;
  visibility: string;
  reservedByUid: string | null;
  usedByUid: string | null;
  usedAt: unknown;
};

export type CouponTrashMetadata = {
  deletedByUid: string;
  purgeAtMillis: number;
  state: CouponTrashState;
};

export type CouponTrashCursor = {
  deletedAtMillis: number;
  couponId: string;
};

export function createCouponTrashMetadata(
  coupon: Record<string, unknown>,
  deletedByUid: string,
  nowMillis: number
): CouponTrashMetadata {
  const status = stringField(coupon.status, "쿠폰 상태가 올바르지 않습니다.");
  const visibility = stringField(coupon.visibility, "쿠폰 공개 범위가 올바르지 않습니다.");
  if (!RESTORABLE_STATUSES.has(status)) {
    throw new Error("삭제할 수 없는 쿠폰 상태입니다.");
  }
  if (!RESTORABLE_VISIBILITIES.has(visibility)) {
    throw new Error("삭제할 수 없는 쿠폰 공개 범위입니다.");
  }

  return {
    deletedByUid,
    purgeAtMillis: nowMillis + COUPON_TRASH_RETENTION_MS,
    state: {
      status,
      visibility,
      reservedByUid: nullableString(coupon.reservedByUid),
      usedByUid: nullableString(coupon.usedByUid),
      usedAt: coupon.usedAt ?? null
    }
  };
}

export function parseCouponTrashState(value: unknown): CouponTrashState | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const state = value as Record<string, unknown>;
  const status = typeof state.status === "string" ? state.status : "";
  const visibility = typeof state.visibility === "string" ? state.visibility : "";
  if (!RESTORABLE_STATUSES.has(status) || !RESTORABLE_VISIBILITIES.has(visibility)) return null;
  return {
    status,
    visibility,
    reservedByUid: nullableString(state.reservedByUid),
    usedByUid: nullableString(state.usedByUid),
    usedAt: state.usedAt ?? null
  };
}

export function canManageDeletedCoupon(
  coupon: Record<string, unknown>,
  uid: string,
  roomOwnerUid: string | null
): boolean {
  if (coupon.ownerUid === uid) return true;
  const state = parseCouponTrashState(coupon.trashState);
  return roomOwnerUid === uid && state?.visibility === "room";
}

export function isCouponTrashStatus(status: unknown): boolean {
  return status === "deleted" || status === "purging";
}

export function isCouponTrashExpired(purgeAtMillis: number, nowMillis: number): boolean {
  return purgeAtMillis <= nowMillis;
}

export function encodeCouponTrashCursor(cursor: CouponTrashCursor): string {
  return Buffer.from(JSON.stringify(cursor), "utf8").toString("base64url");
}

export function decodeCouponTrashCursor(value: string): CouponTrashCursor | null {
  if (value.length === 0 || value.length > 512) return null;
  try {
    const parsed = JSON.parse(Buffer.from(value, "base64url").toString("utf8")) as Record<string, unknown>;
    const deletedAtMillis = parsed.deletedAtMillis;
    const couponId = parsed.couponId;
    if (!isValidDeletedAtMillis(deletedAtMillis) || !isValidCouponId(couponId)) return null;
    return { deletedAtMillis, couponId };
  } catch {
    return null;
  }
}

function isValidDeletedAtMillis(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isValidCouponId(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= 256 && !value.includes("/");
}

function stringField(value: unknown, message: string): string {
  if (typeof value !== "string" || value.length === 0) throw new Error(message);
  return value;
}

function nullableString(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}
