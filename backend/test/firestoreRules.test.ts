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
  Timestamp,
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

  it("accepts only paired, bounded barcode metadata in supported formats", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertSucceeds(setDoc(doc(db, "rooms/room-1/coupons/coupon-barcode"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/coupon-barcode/image.jpg",
      barcodeValue: "8801234567890",
      barcodeFormat: "EAN_13"
    }));
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-unpaired"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/coupon-unpaired/image.jpg",
      barcodeValue: "8801234567890"
    }));
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-long"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/coupon-long/image.jpg",
      barcodeValue: "1".repeat(81),
      barcodeFormat: "CODE_128"
    }));
    await assertFails(setDoc(doc(db, "rooms/room-1/coupons/coupon-invalid-ean"), {
      ...validCoupon(),
      imageBlobPath: "rooms/room-1/coupons/coupon-invalid-ean/image.jpg",
      barcodeValue: "not-a-number",
      barcodeFormat: "EAN_13"
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

  it("lets only the member who marked a coupon used undo it within five minutes", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "used",
        usedByUid: "member-2",
        usedAt: Timestamp.now()
      });
    });
    const update = {
      status: "active",
      reservedByUid: null,
      usedByUid: null,
      usedAt: null,
      updatedAt: serverTimestamp()
    };
    const memberDb = testEnvironment.authenticatedContext("member-2").firestore();
    await assertSucceeds(updateDoc(doc(memberDb, "rooms/room-1/coupons/coupon-1"), update));
  });

  it("blocks another member from undoing a used coupon", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "used",
        usedByUid: "member-2",
        usedAt: Timestamp.now()
      });
    });
    const ownerDb = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(updateDoc(doc(ownerDb, "rooms/room-1/coupons/coupon-1"), {
      status: "active",
      reservedByUid: null,
      usedByUid: null,
      usedAt: null,
      updatedAt: serverTimestamp()
    }));
  });

  it("blocks undo after the five-minute recovery window", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "used",
        usedByUid: "member-2",
        usedAt: Timestamp.fromMillis(Date.now() - 6 * 60 * 1000)
      });
    });
    const memberDb = testEnvironment.authenticatedContext("member-2").firestore();
    await assertFails(updateDoc(doc(memberDb, "rooms/room-1/coupons/coupon-1"), {
      status: "active",
      reservedByUid: null,
      usedByUid: null,
      usedAt: null,
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

  it("hides a soft-deleted coupon and its comments even from the coupon owner", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1"), {
        ...validCoupon(),
        status: "deleted",
        visibility: "deleted",
        deletedByUid: "member-1",
        deletedAt: Timestamp.now(),
        purgeAt: Timestamp.fromMillis(Date.now() + 30 * 24 * 60 * 60 * 1000),
        trashState: { status: "active", visibility: "room" }
      });
      await setDoc(doc(context.firestore(), "rooms/room-1/coupons/coupon-1/comments/comment-1"), {
        authorUid: "member-1",
        body: "삭제된 댓글"
      });
    });
    const ownerDb = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(getDoc(doc(ownerDb, "rooms/room-1/coupons/coupon-1")));
    await assertFails(getDoc(doc(ownerDb, "rooms/room-1/coupons/coupon-1/comments/comment-1")));
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

  it("allows duplicate checks only across room-visible and the caller's private coupons", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      const adminDb = context.firestore();
      await setDoc(doc(adminDb, "rooms/room-1/coupons/room-coupon"), validCoupon());
      await setDoc(doc(adminDb, "rooms/room-1/coupons/own-private"), {
        ...validCoupon(),
        imageBlobPath: "rooms/room-1/coupons/own-private/image.jpg",
        visibility: "private"
      });
      await setDoc(doc(adminDb, "rooms/room-1/coupons/other-private"), {
        ...validCoupon(),
        ownerUid: "member-2",
        imageBlobPath: "rooms/room-1/coupons/other-private/image.jpg",
        visibility: "private"
      });
    });

    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const coupons = collection(db, "rooms/room-1/coupons");
    const roomMatches = await assertSucceeds(getDocs(query(
      coupons,
      where("visibility", "==", "room"),
      where("expiresLocalDate", "==", "2026-12-31"),
      orderBy(documentId()),
      limit(20)
    )));
    const ownPrivateMatches = await assertSucceeds(getDocs(query(
      coupons,
      where("visibility", "==", "private"),
      where("ownerUid", "==", "member-1"),
      where("expiresLocalDate", "==", "2026-12-31"),
      orderBy(documentId()),
      limit(20)
    )));

    expect(roomMatches.docs.map((snapshot) => snapshot.id)).toEqual(["room-coupon"]);
    expect(ownPrivateMatches.docs.map((snapshot) => snapshot.id)).toEqual(["own-private"]);
    await assertFails(getDocs(query(
      coupons,
      where("visibility", "==", "private"),
      where("ownerUid", "==", "member-2"),
      where("expiresLocalDate", "==", "2026-12-31"),
      orderBy(documentId()),
      limit(20)
    )));
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

describe("server-only operational state", () => {
  it("blocks clients from reading or writing outbox, Blob cleanup, and cron lease documents", async () => {
    await testEnvironment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "notificationOutbox/message-1"), { status: "pending" });
      await setDoc(doc(context.firestore(), "blobCleanupQueue/cleanup-1"), { status: "pending" });
      await setDoc(doc(context.firestore(), "cronLeases/expiry-reminders-2026-08-15"), { status: "running" });
    });

    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(getDoc(doc(db, "notificationOutbox/message-1")));
    await assertFails(setDoc(doc(db, "notificationOutbox/message-2"), { status: "sent" }));
    await assertFails(getDoc(doc(db, "blobCleanupQueue/cleanup-1")));
    await assertFails(setDoc(doc(db, "blobCleanupQueue/cleanup-2"), { status: "deleted" }));
    await assertFails(getDoc(doc(db, "cronLeases/expiry-reminders-2026-08-15")));
    await assertFails(setDoc(doc(db, "cronLeases/manual"), { status: "completed" }));
  });
});

describe("favorite security rules", () => {
  it("본인 즐겨찾기는 만들고 읽고 지울 수 있다", async () => {
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    const ref = doc(db, "users/member-1/favorites/room-1__coupon-1");
    await assertSucceeds(
      setDoc(ref, { roomId: "room-1", couponId: "coupon-1", createdAt: serverTimestamp() })
    );
    await assertSucceeds(getDoc(ref));
    await assertSucceeds(getDocs(collection(db, "users/member-1/favorites")));
  });

  it("남의 즐겨찾기는 읽지도 쓰지도 못한다", async () => {
    const db = testEnvironment.authenticatedContext("member-2").firestore();
    const ref = doc(db, "users/member-1/favorites/room-1__coupon-1");
    await assertFails(
      setDoc(ref, { roomId: "room-1", couponId: "coupon-1", createdAt: serverTimestamp() })
    );
    await assertFails(getDoc(ref));
  });

  it("문서 ID 가 roomId__couponId 와 맞지 않으면 거부한다", async () => {
    // ID 규칙이 없으면 같은 쿠폰이 여러 문서로 중복되고 해제도 불가능해진다.
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(
      setDoc(doc(db, "users/member-1/favorites/anything"), {
        roomId: "room-1",
        couponId: "coupon-1",
        createdAt: serverTimestamp()
      })
    );
  });

  it("멤버가 아닌 방의 쿠폰은 즐겨찾기할 수 없다", async () => {
    const db = testEnvironment.authenticatedContext("outsider").firestore();
    await assertFails(
      setDoc(doc(db, "users/outsider/favorites/room-1__coupon-1"), {
        roomId: "room-1",
        couponId: "coupon-1",
        createdAt: serverTimestamp()
      })
    );
  });

  it("허용하지 않은 필드는 거부한다", async () => {
    // 제목을 복사해 두면 쿠폰을 수정했을 때 즐겨찾기만 옛 정보를 보인다.
    const db = testEnvironment.authenticatedContext("member-1").firestore();
    await assertFails(
      setDoc(doc(db, "users/member-1/favorites/room-1__coupon-1"), {
        roomId: "room-1",
        couponId: "coupon-1",
        title: "아메리카노",
        createdAt: serverTimestamp()
      })
    );
  });
});
