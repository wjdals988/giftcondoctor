import { Timestamp } from "firebase-admin/firestore";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import {
  claimDeletedCouponForPurge,
  claimExpiredCouponForPurge,
  listDeletedCoupons,
  restoreDeletedCoupon,
  softDeleteCoupon
} from "../lib/couponTrashStore";
import { decodeCouponTrashCursor } from "../lib/couponTrash";
import { getAdminDb } from "../lib/firebaseAdmin";

describe("coupon trash Firestore integration", () => {
  const roomId = "trash-room";
  const couponId = "trash-coupon";

  beforeAll(() => {
    process.env.FIREBASE_PROJECT_ID = "demo-giftcondoctor";
  });

  beforeEach(async () => {
    const db = getAdminDb();
    await db.recursiveDelete(db.doc(`rooms/${roomId}`));
    await db.doc(`rooms/${roomId}`).set({ name: "복구 테스트방", ownerUid: "owner-1" });
    await Promise.all([
      db.doc(`rooms/${roomId}/members/owner-1`).set({ role: "owner" }),
      db.doc(`rooms/${roomId}/members/member-1`).set({ role: "member" }),
      db.doc(`rooms/${roomId}/coupons/${couponId}`).set({
        title: "아메리카노",
        brand: "스타벅스",
        ownerUid: "owner-1",
        imageBlobPath: `rooms/${roomId}/coupons/${couponId}/original.jpg`,
        thumbnailBlobPath: `rooms/${roomId}/coupons/${couponId}/thumbnail.webp`,
        expiresLocalDate: "2026-12-31",
        status: "active",
        visibility: "room",
        reservedByUid: null,
        usedByUid: null,
        usedAt: null
      }),
      db.doc(`rooms/${roomId}/coupons/${couponId}/comments/comment-1`).set({
        authorUid: "owner-1",
        body: "보존할 댓글"
      })
    ]);
  });

  afterAll(async () => {
    await getAdminDb().recursiveDelete(getAdminDb().doc(`rooms/${roomId}`));
  });

  it("soft deletes, hides from an unrelated member, and restores the coupon with comments", async () => {
    const now = new Date("2026-08-15T00:00:00Z");
    const deleted = await softDeleteCoupon(roomId, couponId, "owner-1", now);
    expect(deleted.title).toBe("아메리카노");
    expect(deleted.purgeAt).toBe("2026-09-14T00:00:00.000Z");

    const db = getAdminDb();
    const trashed = await db.doc(`rooms/${roomId}/coupons/${couponId}`).get();
    expect(trashed.get("status")).toBe("deleted");
    expect(trashed.get("visibility")).toBe("deleted");
    expect((await db.doc(`rooms/${roomId}/coupons/${couponId}/comments/comment-1`).get()).exists).toBe(true);
    expect((await listDeletedCoupons(roomId, "owner-1")).coupons).toHaveLength(1);
    expect((await listDeletedCoupons(roomId, "member-1")).coupons).toHaveLength(0);

    await restoreDeletedCoupon(roomId, couponId, "owner-1", new Date("2026-08-16T00:00:00Z"));
    const restored = await db.doc(`rooms/${roomId}/coupons/${couponId}`).get();
    expect(restored.get("status")).toBe("active");
    expect(restored.get("visibility")).toBe("room");
    expect(restored.get("trashState")).toBeUndefined();
    expect((await db.doc(`rooms/${roomId}/coupons/${couponId}/comments/comment-1`).get()).exists).toBe(true);
  });

  it("rejects restore after expiry and allows an expired purge claim", async () => {
    await softDeleteCoupon(roomId, couponId, "owner-1", new Date("2026-07-01T00:00:00Z"));
    await expect(restoreDeletedCoupon(roomId, couponId, "owner-1", new Date("2026-08-15T00:00:00Z")))
      .rejects.toMatchObject({ status: 410 });
    const ref = getAdminDb().doc(`rooms/${roomId}/coupons/${couponId}`);
    const claimed = await claimExpiredCouponForPurge(ref, new Date("2026-08-15T00:00:00Z"));
    expect(claimed?.exists).toBe(true);
    expect((await ref.get()).get("status")).toBe("purging");
  });

  it("lets an authorized owner claim immediate permanent deletion", async () => {
    await softDeleteCoupon(roomId, couponId, "owner-1", new Date());
    await claimDeletedCouponForPurge(roomId, couponId, "owner-1");
    const coupon = await getAdminDb().doc(`rooms/${roomId}/coupons/${couponId}`).get();
    expect(coupon.get("status")).toBe("purging");
    expect(coupon.get("purgeAt")).toBeInstanceOf(Timestamp);
  });

  it("does not expose a member's private trashed coupon to the room owner", async () => {
    const ref = getAdminDb().doc(`rooms/${roomId}/coupons/${couponId}`);
    await ref.update({ ownerUid: "member-1", visibility: "private" });
    await softDeleteCoupon(roomId, couponId, "member-1", new Date("2026-08-15T00:00:00Z"));

    expect((await listDeletedCoupons(roomId, "owner-1")).coupons).toEqual([]);
    await expect(restoreDeletedCoupon(roomId, couponId, "owner-1"))
      .rejects.toMatchObject({ status: 403 });
    await expect(claimDeletedCouponForPurge(roomId, couponId, "owner-1"))
      .rejects.toMatchObject({ status: 403 });
    expect((await listDeletedCoupons(roomId, "member-1")).coupons).toHaveLength(1);
  });

  it("pages more than one hundred deleted coupons without duplicates", async () => {
    const db = getAdminDb();
    const writes = db.batch();
    const deletedAtBase = Date.parse("2026-08-15T00:00:00Z");
    for (let index = 0; index < 105; index += 1) {
      const id = `paged-${index.toString().padStart(3, "0")}`;
      writes.set(db.doc(`rooms/${roomId}/coupons/${id}`), {
        title: `삭제 쿠폰 ${index}`,
        brand: "페이징",
        ownerUid: "owner-1",
        imageBlobPath: `rooms/${roomId}/coupons/${id}/original.jpg`,
        expiresLocalDate: "2026-12-31",
        status: "deleted",
        visibility: "deleted",
        deletedAt: Timestamp.fromMillis(deletedAtBase - index * 1_000),
        purgeAt: Timestamp.fromMillis(deletedAtBase + 30 * 24 * 60 * 60 * 1_000),
        trashState: {
          status: "active",
          visibility: "room",
          reservedByUid: null,
          usedByUid: null,
          usedAt: null
        }
      });
    }
    await writes.commit();

    const collected: string[] = [];
    let cursor = null;
    do {
      const page = await listDeletedCoupons(roomId, "owner-1", cursor);
      collected.push(...page.coupons.map((coupon) => coupon.couponId));
      cursor = page.nextCursor ? decodeCouponTrashCursor(page.nextCursor) : null;
    } while (cursor);

    expect(collected).toHaveLength(105);
    expect(new Set(collected).size).toBe(105);
    expect(collected[0]).toBe("paged-000");
    expect(collected.at(-1)).toBe("paged-104");
  });
});
