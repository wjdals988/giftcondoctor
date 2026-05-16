import { FieldValue } from "firebase-admin/firestore";
import type { BatchResponse } from "firebase-admin/messaging";
import { getAdminDb, getAdminMessaging } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, requireCronSecret } from "@/lib/http";
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
      deepLink: `giftcondoctor://rooms/${params.roomId}/coupons/${params.couponId}`
    },
    android: {
      priority: "high",
      notification: {
        channelId: "coupon_expiry",
        clickAction: "OPEN_COUPON_DETAIL"
      }
    }
  });

  await removeInvalidTokens(params.uid, tokenDocs, response);
  return { sent: response.successCount, skipped: response.failureCount };
}

async function runExpiryReminders(now = new Date()): Promise<Summary> {
  const db = getAdminDb();
  const today = seoulLocalDate(now);
  const dates = targetDates(today);
  const targetDateValues = dates.map((item) => item.targetDate);
  const summary: Summary = { scanned: 0, matched: 0, sent: 0, skipped: 0, errors: [] };

  const coupons = await db
    .collectionGroup("coupons")
    .where("status", "in", ["active", "reserved"])
    .where("expiresLocalDate", "in", targetDateValues)
    .get();

  summary.scanned = coupons.size;

  for (const coupon of coupons.docs) {
    const roomRef = coupon.ref.parent.parent;
    if (!roomRef) {
      summary.skipped += 1;
      continue;
    }

    const roomId = roomRef.id;
    const couponId = coupon.id;
    const couponData = coupon.data();
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
    if (existingLog.exists) {
      summary.skipped += 1;
      continue;
    }

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
      const user = await db.doc(`users/${uid}`).get();
      if (!user.exists || user.get("pushEnabled") === false) {
        summary.skipped += 1;
        continue;
      }

      if (!shouldNotify(daysBefore, user.data(), room.data(), member.data())) {
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
          body: notificationBody(couponData.title, expiresLocalDate)
        });
        summary.sent += result.sent;
        summary.skipped += result.skipped;
        if (result.sent > 0) sentToUids.push(uid);
      } catch (error) {
        summary.errors.push(error instanceof Error ? error.message : String(error));
      }
    }

    await logRef.set({
      roomId,
      couponId,
      daysBefore,
      targetDate: expiresLocalDate,
      sentToUids,
      sentAt: FieldValue.serverTimestamp()
    });
  }

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
