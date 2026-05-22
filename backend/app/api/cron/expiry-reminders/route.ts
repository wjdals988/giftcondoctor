import { FieldValue } from "firebase-admin/firestore";
import type { BatchResponse } from "firebase-admin/messaging";
import { getAdminDb, getAdminMessaging } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, requireCronSecret } from "@/lib/http";
import { PUSH_TEST_ROOM_ID, ensurePushTestRoom } from "@/lib/pushTestRoom";
import {
  daysBetweenLocalDates,
  isInvalidFcmTokenCode,
  notificationBody,
  notificationLogId,
  notificationTitle,
  seoulLocalDate,
  shouldNotify,
  targetDates
} from "@/lib/reminders";

export const runtime = "nodejs";
export const maxDuration = 300;

type Summary = {
  scanned: number;
  matched: number;
  sent: number;
  skipped: number;
  errors: string[];
};

type TokenDoc = {
  id: string;
  token: string;
};

async function tokenDocsForUid(uid: string): Promise<TokenDoc[]> {
  const tokens = await getAdminDb().collection(`users/${uid}/pushTokens`).get();
  return tokens.docs
    .map((doc) => ({ id: doc.id, token: doc.get("token") }))
    .filter((doc): doc is TokenDoc => typeof doc.token === "string" && doc.token.length > 0);
}

async function removeInvalidTokens(uid: string, tokenDocs: TokenDoc[], response: BatchResponse) {
  const db = getAdminDb();
  const batch = db.batch();
  let hasDeletes = false;

  response.responses.forEach((item, index) => {
    if (!item.success && isInvalidFcmTokenCode(item.error?.code)) {
      batch.delete(db.doc(`users/${uid}/pushTokens/${tokenDocs[index].id}`));
      hasDeletes = true;
    }
  });

  if (hasDeletes) {
    await batch.commit();
  }
}

async function sendToUid(params: {
  uid: string;
  roomId: string;
  couponId: string;
  daysBefore: number;
  title: string;
  body: string;
  deepLink?: string;
  type?: string;
}) {
  const tokenDocs = await tokenDocsForUid(params.uid);
  if (tokenDocs.length === 0) return { sent: 0, skipped: 1 };

  const response = await getAdminMessaging().sendEachForMulticast({
    tokens: tokenDocs.map((doc) => doc.token),
    notification: {
      title: params.title,
      body: params.body
    },
    data: {
      roomId: params.roomId,
      couponId: params.couponId,
      daysBefore: String(params.daysBefore),
      deepLink: params.deepLink ?? `giftcondoctor://rooms/${params.roomId}/coupons/${params.couponId}`,
      type: params.type ?? "expiry_reminder"
    },
    android: {
      priority: "high",
      notification: {
        channelId: "coupon_expiry",
        clickAction: "OPEN_COUPON_DETAIL",
        color: "#00B4A6",
        icon: "ic_stat_gd_notification"
      }
    }
  });

  await removeInvalidTokens(params.uid, tokenDocs, response);
  return { sent: response.successCount, skipped: response.failureCount };
}

async function runDailyPushTestRoom(today: string): Promise<Pick<Summary, "matched" | "sent" | "skipped" | "errors">> {
  const db = getAdminDb();
  const summary = { matched: 0, sent: 0, skipped: 0, errors: [] as string[] };
  const roomRef = await ensurePushTestRoom();
  const members = await roomRef.collection("members").get();
  if (members.empty) return summary;

  const logId = notificationLogId(PUSH_TEST_ROOM_ID, "daily-push-test", 0, today);
  const logRef = db.doc(`notificationLogs/${logId}`);
  const existingLog = await logRef.get();
  if (existingLog.exists) {
    summary.skipped += members.size;
    return summary;
  }

  const sentToUids: string[] = [];
  for (const member of members.docs) {
    const uid = member.id;
    const user = await db.doc(`users/${uid}`).get();
    if (!user.exists || user.get("pushEnabled") === false) {
      summary.skipped += 1;
      continue;
    }

    summary.matched += 1;
    try {
      const result = await sendToUid({
        uid,
        roomId: PUSH_TEST_ROOM_ID,
        couponId: "daily-push-test",
        daysBefore: 0,
        title: "푸시 테스트방 알림이에요",
        body: "매일 오전 9시 실제 알림 경로가 정상인지 확인하고 있어요.",
        deepLink: `giftcondoctor://rooms/${PUSH_TEST_ROOM_ID}`,
        type: "daily_push_test"
      });
      summary.sent += result.sent;
      summary.skipped += result.skipped;
      if (result.sent > 0) sentToUids.push(uid);
    } catch (error) {
      summary.errors.push(error instanceof Error ? error.message : String(error));
    }
  }

  if (sentToUids.length > 0) {
    await logRef.set({
      roomId: PUSH_TEST_ROOM_ID,
      couponId: "daily-push-test",
      daysBefore: 0,
      targetDate: today,
      sentToUids,
      kind: "dailyPushTest",
      sentAt: FieldValue.serverTimestamp()
    });
  }

  return summary;
}

async function runExpiryReminders(now = new Date()): Promise<Summary> {
  const db = getAdminDb();
  const today = seoulLocalDate(now);
  const dates = targetDates(today);
  const targetDateValues = dates.map((item) => item.targetDate);
  const summary: Summary = { scanned: 0, matched: 0, sent: 0, skipped: 0, errors: [] };

  const couponSnapshots = await Promise.all(
    targetDateValues.map((targetDate) =>
      db.collectionGroup("coupons").where("expiresLocalDate", "==", targetDate).get()
    )
  );
  const couponDocs = couponSnapshots.flatMap((snapshot) => snapshot.docs);

  summary.scanned = couponDocs.length;

  for (const coupon of couponDocs) {
    const roomRef = coupon.ref.parent.parent;
    if (!roomRef) {
      summary.skipped += 1;
      continue;
    }

    const roomId = roomRef.id;
    const couponId = coupon.id;
    const couponData = coupon.data();
    if (couponData.status !== "active" && couponData.status !== "reserved") {
      summary.skipped += 1;
      continue;
    }

    const expiresLocalDate = couponData.expiresLocalDate;
    if (typeof expiresLocalDate !== "string") {
      summary.skipped += 1;
      continue;
    }

    const daysBefore = daysBetweenLocalDates(today, expiresLocalDate);
    if (!targetDateValues.includes(expiresLocalDate) || daysBefore < 0) {
      summary.skipped += 1;
      continue;
    }

    const logId = notificationLogId(roomId, couponId, daysBefore, expiresLocalDate);
    const logRef = db.doc(`notificationLogs/${logId}`);
    const existingLog = await logRef.get();
    const existingSentToUids = existingLog.exists ? existingLog.get("sentToUids") : undefined;
    const alreadySentUids = new Set(
      (existingSentToUids as unknown[] | undefined)?.filter((value): value is string => typeof value === "string") ?? []
    );

    const room = await roomRef.get();
    if (!room.exists) {
      summary.skipped += 1;
      continue;
    }

    const ownerUid = couponData.ownerUid;
    if (typeof ownerUid !== "string") {
      summary.skipped += 1;
      continue;
    }

    const members = await roomRef.collection("members").get();
    const visibility = couponData.visibility ?? "room";
    const notifyTarget = couponData.notifyTarget ?? "allMembers";
    const targetMembers = members.docs.filter((member) => {
      if (visibility === "private") return member.id === ownerUid;
      if (notifyTarget === "ownerOnly") return member.id === ownerUid;
      return true;
    });

    const sentToUids: string[] = [];
    for (const member of targetMembers) {
      const uid = member.id;
      if (alreadySentUids.has(uid)) {
        summary.skipped += 1;
        continue;
      }

      const user = await db.doc(`users/${uid}`).get();
      if (!user.exists || user.get("pushEnabled") === false) {
        summary.skipped += 1;
        continue;
      }

      if (!shouldNotify(daysBefore, user.data())) {
        summary.skipped += 1;
        continue;
      }

      summary.matched += 1;
      try {
        const result = await sendToUid({
          uid,
          roomId,
          couponId,
          daysBefore,
          title: notificationTitle(couponData.brand, daysBefore),
          body: notificationBody(couponData.title, expiresLocalDate, daysBefore)
        });
        summary.sent += result.sent;
        summary.skipped += result.skipped;
        if (result.sent > 0) sentToUids.push(uid);
      } catch (error) {
        summary.errors.push(error instanceof Error ? error.message : String(error));
      }
    }

    if (sentToUids.length === 0) {
      if (targetMembers.length === 0) summary.skipped += 1;
      continue;
    }

    await logRef.set({
      roomId,
      couponId,
      daysBefore,
      targetDate: expiresLocalDate,
      sentToUids: FieldValue.arrayUnion(...sentToUids),
      sentAt: FieldValue.serverTimestamp()
    }, { merge: true });
  }

  const testRoomSummary = await runDailyPushTestRoom(today);
  summary.matched += testRoomSummary.matched;
  summary.sent += testRoomSummary.sent;
  summary.skipped += testRoomSummary.skipped;
  summary.errors.push(...testRoomSummary.errors);

  return summary;
}

async function handle(request: Request) {
  try {
    requireCronSecret(request);
    return json(await runExpiryReminders());
  } catch (error) {
    if (error instanceof ApiError) return jsonError(error);
    return jsonError(error);
  }
}

export async function GET(request: Request) {
  return handle(request);
}

export async function POST(request: Request) {
  return handle(request);
}
