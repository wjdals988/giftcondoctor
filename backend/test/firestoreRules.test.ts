import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment
} from "@firebase/rules-unit-testing";
import {
  collection,
  doc,
  documentId,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  startAfter,
  updateDoc,
  where
} from "firebase/firestore";
import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";

const projectId = "demo-giftcondoctor";
let testEnvironment: RulesTestEnvironment;

function validCoupon() {
  return {
    title: "아메리카노",
    brand: "스타벅스",
    ownerUid: "member-1",
    imageBlobPath: "rooms/room-1/coupons/coupon-1/image.jpg",
    imageWidth: null,
    imageHeight: null,
    expiresLocalDate: "2026-12-31",
    expiresAtUtc: serverTimestamp(),
    timezone: "Asia/Seoul",
    status: "active",
    reservedByUid: null,
    usedByUid: null,
    usedAt: null,
    visibility: "room",
    notifyTarget: "allMembers",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
}

beforeAll(async () => {
  const [host, portText] = (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":");
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host,
      port: Number(portText),
      rules: readFileSync("../firebase/firestore.rules", "utf8")
    }
  });
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "rooms/room-1"), {
      name: "테스트방",
      ownerUid: "member-1"
    });
    await setDoc(doc(context.firestore(), "rooms/room-1/members/member-1"), { role: "owner" });
    await setDoc(doc(context.firestore(), "rooms/room-1/members/member-2"), { role: "member" });
  });
});

afterAll(async () => {
  await testEnvironment.cleanup();
});

describe("coupon security rules", () => {
  it("allows a member to create a schema-valid coupon", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertSucceeds(setDoc(doc(db, "rooms/room-1/coupons/coupon-1"), validCoupon()));
  });

  it("allows only a thumbnail path owned by the same coupon", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertSucceeds(setDoc(doc(db, "rooms/room-1/coupons/coupon-1"), {
      ...validCoupon(),
      thumbnailBlobPath: "rooms/room-1/coupons/coupon-1/thumbnail.webp"
    }));
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-2"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/coupon-2/image.jpg",
      thumbnailBlobPath: "rooms/room-1/coupons/coupon-1/thumbnail.webp"
    }));
  });

  it("rejects a Blob path owned by another coupon", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-1"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/other/image.jpg"
    }));
  });

  it("rejects malformed dates and unknown fields", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-1"), {
      ...validCoupon(),
      expiresLocalDate: "31/12/2026",
      injected: true
    }));
  });

  it("rejects an invalid owner edit after creation", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const couponRef = doc(db, "rooms/room-1/coupons/coupon-1");
    await assertSucceeds(setDoc(couponRef, validCoupon()));
    await assertFails(updateDoc(couponRef, { title: "x".repeat(101) }));
  });

  it("rejects replacing the immutable thumbnail path", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const couponRef = doc(db, "rooms/room-1/coupons/coupon-1");
    await assertSucceeds(setDoc(couponRef, {
      ...validCoupon(),
      thumbnailBlobPath: "rooms/room-1/coupons/coupon-1/thumbnail.webp"
    }));
    await assertFails(updateDoc(couponRef, {
      thumbnailBlobPath: "rooms/room-1/coupons/coupon-1/replacement.webp"
    }));
  });

  it("blocks a different member from using a reserved coupon", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "reserved",
        reservedByUid: "member-1"
      });
    });
    const db = testEnvironment.authenticatedContext("member-2").firestore();
    await assertFails(updateDoc(doc(db, "rooms/room-1/coupons/coupon-1"), {
      status: "used",
      reservedByUid: null,
      usedByUid: "member-2",
      usedAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    }));
  });

  it("lets the reserver mark a coupon used and clears the reservation", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "reserved",
        reservedByUid: "member-2"
      });
    });
    const db = testEnvironment.authenticatedContext("member-2").firestore();
    await assertSucceeds(updateDoc(doc(db, "rooms/room-1/coupons/coupon-1"), {
      status: "used",
      reservedByUid: null,
      usedByUid: "member-2",
      usedAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    }));
  });

  it("blocks a non-member from reading a coupon", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), validCoupon());
    });
    const db = testEnvironment.authenticatedContext("outsider").firestore();
    await assertFails(getDoc(doc(db, "rooms/room-1/coupons/coupon-1")));
  });

  it("allows bounded cursor queries but rejects another owner's private coupon query", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, "rooms/room-1/coupons/coupon-1"), validCoupon());
      await setDoc(doc(adminDb, "rooms/room-1/coupons/coupon-2"), {
        ...validCoupon(),
        imageBlobPath: "rooms/room-1/coupons/coupon-2/image.jpg",
        expiresLocalDate: "2027-01-01"
      });
      await setDoc(doc(adminDb, "rooms/room-1/coupons/coupon-3"), {
        ...validCoupon(),
        imageBlobPath: "rooms/room-1/coupons/coupon-3/image.jpg",
        visibility: "private"
      });
      await setDoc(doc(adminDb, "rooms/room-1/coupons/coupon-4"), {
        ...validCoupon(),
        ownerUid: "member-2",
        imageBlobPath: "rooms/room-1/coupons/coupon-4/image.jpg",
        visibility: "private"
      });
    });

    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const coupons = collection(db, "rooms/room-1/coupons");
    const roomPage = query(
      coupons,
      where("visibility", "==", "room"),
      orderBy("expiresLocalDate"),
      orderBy(documentId()),
      limit(2)
    );
    const firstPage = await assertSucceeds(getDocs(roomPage));
    expect(firstPage.size).toBe(2);
    await assertSucceeds(getDocs(query(roomPage, startAfter(firstPage.docs[0]), limit(1))));

    const ownPrivatePage = query(
      coupons,
      where("visibility", "==", "private"),
      where("ownerUid", "==", "member-1"),
      orderBy("expiresLocalDate"),
      orderBy(documentId()),
      limit(2)
    );
    await assertSucceeds(getDocs(ownPrivatePage));
    await assertFails(getDocs(query(coupons, where("visibility", "==", "private"), limit(2))));
  });
});

describe("push token security rules", () => {
  it("rejects unexpected fields in a user push token", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const tokenId = "a".repeat(64);
    await assertFails(setDoc(doc(db, `users/member-1/pushTokens/${tokenId}`), {
      token: "x".repeat(32),
      platform: "android",
      deviceName: "test device",
      appVersion: "0.1.13",
      createdAt: serverTimestamp(),
      lastSeenAt: serverTimestamp(),
      admin: true
    }));
  });
});

describe("notification setting security rules", () => {
  it("rejects reminder days outside the cron scan policy", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(setDoc(doc(db, "users/member-1"), {
      defaultNotificationMode: "basic",
      defaultNotificationDays: [10, 0],
      pushEnabled: true
    }));
  });
});
