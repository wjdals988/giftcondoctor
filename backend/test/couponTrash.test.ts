import { describe, expect, it } from "vitest";
import {
  COUPON_TRASH_RETENTION_MS,
  canManageDeletedCoupon,
  createCouponTrashMetadata,
  decodeCouponTrashCursor,
  encodeCouponTrashCursor,
  isCouponTrashExpired,
  isCouponTrashStatus,
  parseCouponTrashState
} from "../lib/couponTrash";

describe("coupon trash policy", () => {
  const coupon = {
    ownerUid: "owner-1",
    status: "reserved",
    visibility: "private",
    reservedByUid: "member-1",
    usedByUid: null,
    usedAt: null
  };

  it("preserves the restorable state for thirty days", () => {
    const metadata = createCouponTrashMetadata(coupon, "owner-1", 1_000);
    expect(metadata.deletedByUid).toBe("owner-1");
    expect(metadata.purgeAtMillis).toBe(1_000 + COUPON_TRASH_RETENTION_MS);
    expect(metadata.state).toEqual({
      status: "reserved",
      visibility: "private",
      reservedByUid: "member-1",
      usedByUid: null,
      usedAt: null
    });
  });

  it("rejects an already deleted coupon", () => {
    expect(() => createCouponTrashMetadata({ ...coupon, status: "deleted" }, "owner-1", 0))
      .toThrow("삭제할 수 없는 쿠폰 상태");
  });

  it("parses only valid restorable states", () => {
    expect(parseCouponTrashState({ status: "used", visibility: "room", usedByUid: "member-1" }))
      .toMatchObject({ status: "used", visibility: "room", usedByUid: "member-1" });
    expect(parseCouponTrashState({ status: "deleted", visibility: "room" })).toBeNull();
    expect(parseCouponTrashState(null)).toBeNull();
  });

  it("allows the coupon owner or room owner to manage trash", () => {
    const publicTrashedCoupon = {
      ownerUid: "owner-1",
      trashState: {
        status: "active",
        visibility: "room",
        reservedByUid: null,
        usedByUid: null,
        usedAt: null
      }
    };
    expect(canManageDeletedCoupon(publicTrashedCoupon, "owner-1", "room-owner")).toBe(true);
    expect(canManageDeletedCoupon(publicTrashedCoupon, "room-owner", "room-owner")).toBe(true);
    expect(canManageDeletedCoupon(publicTrashedCoupon, "member-2", "room-owner")).toBe(false);
  });

  it("keeps a private trashed coupon exclusive to its owner", () => {
    const privateCoupon = {
      ownerUid: "owner-1",
      trashState: {
        status: "active",
        visibility: "private",
        reservedByUid: null,
        usedByUid: null,
        usedAt: null
      }
    };

    expect(canManageDeletedCoupon(privateCoupon, "owner-1", "room-owner")).toBe(true);
    expect(canManageDeletedCoupon(privateCoupon, "room-owner", "room-owner")).toBe(false);
  });

  it("recognizes purge states and deadline", () => {
    expect(isCouponTrashStatus("deleted")).toBe(true);
    expect(isCouponTrashStatus("purging")).toBe(true);
    expect(isCouponTrashStatus("active")).toBe(false);
    expect(isCouponTrashExpired(1_000, 999)).toBe(false);
    expect(isCouponTrashExpired(1_000, 1_000)).toBe(true);
  });

  it("round-trips only bounded coupon trash cursors", () => {
    const cursor = { deletedAtMillis: 1_786_742_400_000, couponId: "coupon-20" };
    expect(decodeCouponTrashCursor(encodeCouponTrashCursor(cursor))).toEqual(cursor);
    expect(decodeCouponTrashCursor("not-json")).toBeNull();
    expect(decodeCouponTrashCursor(encodeCouponTrashCursor({ ...cursor, couponId: "rooms/bad" }))).toBeNull();
  });
});
