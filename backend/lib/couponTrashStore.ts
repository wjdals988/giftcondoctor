import {
  FieldPath,
  FieldValue,
  Timestamp,
  type CollectionReference,
  type DocumentData,
  type DocumentReference,
  type DocumentSnapshot,
  type Query,
  type QueryDocumentSnapshot
} from "firebase-admin/firestore";
import { requireCouponBlobPath } from "./blobPath";
import { enqueueBlobCleanup } from "./blobCleanupQueue";
import {
  canManageDeletedCoupon,
  createCouponTrashMetadata,
  encodeCouponTrashCursor,
  isCouponTrashExpired,
  isCouponTrashStatus,
  parseCouponTrashState,
  type CouponTrashCursor
} from "./couponTrash";
import { deleteCouponImages } from "./couponImageStorage";
import { getAdminDb } from "./firebaseAdmin";
import { deleteDocumentRefs } from "./firestoreDelete";
import { ApiError } from "./http";

export type DeletedCouponSummary = {
  couponId: string;
  title: string;
  brand: string;
  expiresLocalDate: string;
  deletedAt: string;
  purgeAt: string;
};

export type DeletedCouponPage = {
  coupons: DeletedCouponSummary[];
  nextCursor: string | null;
};

export const DELETED_COUPON_PAGE_SIZE = 20;

export async function softDeleteCoupon(
  roomId: string,
  couponId: string,
  uid: string,
  now = new Date()
): Promise<DeletedCouponSummary> {
  const db = getAdminDb();
  const roomRef = db.doc(`rooms/${roomId}`);
  const memberRef = db.doc(`rooms/${roomId}/members/${uid}`);
  const couponRef = db.doc(`rooms/${roomId}/coupons/${couponId}`);

  return db.runTransaction(async (transaction) => {
    const [room, member, coupon] = await transaction.getAll(roomRef, memberRef, couponRef);
    if (!member.exists) throw new ApiError(403, "방 멤버만 접근할 수 있습니다.");
    if (!coupon.exists) throw new ApiError(404, "쿠폰을 찾을 수 없습니다.");
    const data = coupon.data() ?? {};
    assertCanManageLiveCoupon(data, uid, room.get("ownerUid"));

    let metadata;
    try {
      metadata = createCouponTrashMetadata(data, uid, now.getTime());
    } catch (error) {
      throw new ApiError(409, error instanceof Error ? error.message : "삭제할 수 없는 쿠폰입니다.");
    }
    const deletedAt = Timestamp.fromDate(now);
    const purgeAt = Timestamp.fromMillis(metadata.purgeAtMillis);
    transaction.update(couponRef, {
      status: "deleted",
      visibility: "deleted",
      reservedByUid: null,
      usedByUid: null,
      usedAt: null,
      deletedByUid: uid,
      deletedAt,
      purgeAt,
      trashState: metadata.state,
      updatedAt: FieldValue.serverTimestamp()
    });

    return summary(couponId, data, deletedAt, purgeAt);
  });
}

export async function listDeletedCoupons(
  roomId: string,
  uid: string,
  cursor: CouponTrashCursor | null = null
): Promise<DeletedCouponPage> {
  const db = getAdminDb();
  const [room, member] = await Promise.all([
    db.doc(`rooms/${roomId}`).get(),
    db.doc(`rooms/${roomId}/members/${uid}`).get()
  ]);
  if (!member.exists) throw new ApiError(403, "방 멤버만 접근할 수 있습니다.");

  const roomOwnerUid = stringOrNull(room.get("ownerUid"));
  const coupons = db.collection(`rooms/${roomId}/coupons`);
  const sources = deletedCouponSources(coupons, uid, roomOwnerUid === uid);
  const sourceDocs = await Promise.all(sources.map((source) => fetchDeletedCouponSource(source, cursor)));
  const visible = mergeVisibleDeletedCoupons(sourceDocs.flat(), uid, roomOwnerUid);
  return createDeletedCouponPage(visible);
}

export async function restoreDeletedCoupon(
  roomId: string,
  couponId: string,
  uid: string,
  now = new Date()
) {
  const db = getAdminDb();
  const roomRef = db.doc(`rooms/${roomId}`);
  const memberRef = db.doc(`rooms/${roomId}/members/${uid}`);
  const couponRef = db.doc(`rooms/${roomId}/coupons/${couponId}`);

  await db.runTransaction(async (transaction) => {
    const [room, member, coupon] = await transaction.getAll(roomRef, memberRef, couponRef);
    if (!member.exists) throw new ApiError(403, "방 멤버만 접근할 수 있습니다.");
    if (!coupon.exists) throw new ApiError(404, "삭제된 쿠폰을 찾을 수 없습니다.");
    const data = coupon.data() ?? {};
    if (data.status !== "deleted") throw new ApiError(409, "복원할 수 없는 쿠폰 상태입니다.");
    if (!canManageDeletedCoupon(data, uid, stringOrNull(room.get("ownerUid")))) {
      throw new ApiError(403, "쿠폰 등록자 또는 방장만 복원할 수 있습니다.");
    }
    const purgeAt = timestampOrNull(data.purgeAt);
    const state = parseCouponTrashState(data.trashState);
    if (!purgeAt || !state) throw new ApiError(409, "복원 정보가 손상되었습니다.");
    if (isCouponTrashExpired(purgeAt.toMillis(), now.getTime())) {
      throw new ApiError(410, "복원 기간이 지났습니다.");
    }

    transaction.update(couponRef, {
      status: state.status,
      visibility: state.visibility,
      reservedByUid: state.reservedByUid,
      usedByUid: state.usedByUid,
      usedAt: state.usedAt,
      deletedByUid: FieldValue.delete(),
      deletedAt: FieldValue.delete(),
      purgeAt: FieldValue.delete(),
      trashState: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp()
    });
  });
}

export async function claimDeletedCouponForPurge(
  roomId: string,
  couponId: string,
  uid: string
): Promise<DocumentReference> {
  const db = getAdminDb();
  const roomRef = db.doc(`rooms/${roomId}`);
  const memberRef = db.doc(`rooms/${roomId}/members/${uid}`);
  const couponRef = db.doc(`rooms/${roomId}/coupons/${couponId}`);
  await db.runTransaction(async (transaction) => {
    const [room, member, coupon] = await transaction.getAll(roomRef, memberRef, couponRef);
    if (!member.exists) throw new ApiError(403, "방 멤버만 접근할 수 있습니다.");
    if (!coupon.exists) throw new ApiError(404, "삭제된 쿠폰을 찾을 수 없습니다.");
    const data = coupon.data() ?? {};
    if (data.status !== "deleted") throw new ApiError(409, "영구 삭제할 수 없는 쿠폰 상태입니다.");
    if (!canManageDeletedCoupon(data, uid, stringOrNull(room.get("ownerUid")))) {
      throw new ApiError(403, "쿠폰 등록자 또는 방장만 영구 삭제할 수 있습니다.");
    }
    transaction.update(couponRef, {
      status: "purging",
      purgeAt: Timestamp.now(),
      updatedAt: FieldValue.serverTimestamp()
    });
  });
  return couponRef;
}

export async function claimExpiredCouponForPurge(
  couponRef: DocumentReference,
  now: Date
): Promise<DocumentSnapshot | null> {
  const db = getAdminDb();
  return db.runTransaction(async (transaction) => {
    const coupon = await transaction.get(couponRef);
    if (!coupon.exists) return null;
    const data = coupon.data() ?? {};
    if (!isCouponTrashStatus(data.status)) return null;
    const purgeAt = timestampOrNull(data.purgeAt);
    if (!purgeAt || !isCouponTrashExpired(purgeAt.toMillis(), now.getTime())) return null;
    if (data.status === "deleted") {
      transaction.update(couponRef, {
        status: "purging",
        updatedAt: FieldValue.serverTimestamp()
      });
    }
    return coupon;
  });
}

export async function purgeCouponDocument(coupon: DocumentSnapshot): Promise<{ cleanupPending: boolean }> {
  if (!coupon.exists || !isCouponTrashStatus(coupon.get("status"))) {
    throw new ApiError(409, "영구 삭제할 수 없는 쿠폰 상태입니다.");
  }
  const couponId = coupon.id;
  const roomId = coupon.ref.parent.parent?.id;
  if (!roomId) throw new Error("쿠폰 방 경로를 확인할 수 없습니다.");
  const paths = [requireCouponBlobPath(coupon.get("imageBlobPath"), roomId, couponId)];
  const thumbnail = coupon.get("thumbnailBlobPath");
  if (thumbnail) paths.push(requireCouponBlobPath(thumbnail, roomId, couponId));

  const cleanupJob = await enqueueBlobCleanup(roomId, couponId, paths);
  const db = getAdminDb();
  const comments = await coupon.ref.collection("comments").get();
  await deleteDocumentRefs(db, [...comments.docs.map((comment) => comment.ref), coupon.ref]);

  try {
    await deleteCouponImages(paths);
    await cleanupJob.delete().catch((error) => {
      console.error("failed to remove completed trash cleanup job", { roomId, couponId, error });
    });
    return { cleanupPending: false };
  } catch {
    return { cleanupPending: true };
  }
}

function assertCanManageLiveCoupon(data: DocumentData, uid: string, roomOwnerUid: unknown) {
  const visibility = data.visibility ?? "room";
  const ownerUid = data.ownerUid;
  if (visibility === "private") {
    if (ownerUid !== uid) throw new ApiError(403, "비공개 쿠폰은 등록자만 삭제할 수 있습니다.");
    return;
  }
  if (ownerUid !== uid && roomOwnerUid !== uid) {
    throw new ApiError(403, "쿠폰 등록자 또는 방장만 삭제할 수 있습니다.");
  }
}

function summaryFromDeleted(coupon: QueryDocumentSnapshot): DeletedCouponSummary | null {
  const deletedAt = timestampOrNull(coupon.get("deletedAt"));
  const purgeAt = timestampOrNull(coupon.get("purgeAt"));
  if (!deletedAt || !purgeAt || !parseCouponTrashState(coupon.get("trashState"))) return null;
  return summary(coupon.id, coupon.data(), deletedAt, purgeAt);
}

function summary(
  couponId: string,
  data: DocumentData,
  deletedAt: Timestamp,
  purgeAt: Timestamp
): DeletedCouponSummary {
  return {
    couponId,
    title: typeof data.title === "string" ? data.title : "이름 없는 쿠폰",
    brand: typeof data.brand === "string" ? data.brand : "",
    expiresLocalDate: typeof data.expiresLocalDate === "string" ? data.expiresLocalDate : "",
    deletedAt: deletedAt.toDate().toISOString(),
    purgeAt: purgeAt.toDate().toISOString()
  };
}

function timestampOrNull(value: unknown): Timestamp | null {
  return value instanceof Timestamp ? value : null;
}

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function compareDeletedCoupons(left: DeletedCouponSummary, right: DeletedCouponSummary): number {
  const byDate = right.deletedAt.localeCompare(left.deletedAt);
  return byDate !== 0 ? byDate : right.couponId.localeCompare(left.couponId);
}

function deletedCouponSources(
  coupons: CollectionReference<DocumentData>,
  uid: string,
  isRoomOwner: boolean
): Query<DocumentData>[] {
  const own = coupons.where("status", "==", "deleted").where("ownerUid", "==", uid);
  if (!isRoomOwner) return [own];
  const shared = coupons
    .where("status", "==", "deleted")
    .where("trashState.visibility", "==", "room");
  return [own, shared];
}

async function fetchDeletedCouponSource(
  source: Query<DocumentData>,
  cursor: CouponTrashCursor | null
): Promise<QueryDocumentSnapshot[]> {
  let query = source.orderBy("deletedAt", "desc").orderBy(FieldPath.documentId(), "desc");
  if (cursor) query = query.startAfter(Timestamp.fromMillis(cursor.deletedAtMillis), cursor.couponId);
  return (await query.limit(DELETED_COUPON_PAGE_SIZE + 1).get()).docs;
}

function mergeVisibleDeletedCoupons(
  documents: QueryDocumentSnapshot[],
  uid: string,
  roomOwnerUid: string | null
): DeletedCouponSummary[] {
  const unique = new Map(documents.map((document) => [document.id, document]));
  return [...unique.values()]
    .filter((document) => canManageDeletedCoupon(document.data(), uid, roomOwnerUid))
    .map(summaryFromDeleted)
    .filter((coupon): coupon is DeletedCouponSummary => coupon !== null)
    .sort(compareDeletedCoupons);
}

function createDeletedCouponPage(visible: DeletedCouponSummary[]): DeletedCouponPage {
  const coupons = visible.slice(0, DELETED_COUPON_PAGE_SIZE);
  const last = coupons.at(-1);
  const nextCursor = visible.length > DELETED_COUPON_PAGE_SIZE && last
    ? encodeCouponTrashCursor({ deletedAtMillis: Date.parse(last.deletedAt), couponId: last.couponId })
    : null;
  return { coupons, nextCursor };
}
