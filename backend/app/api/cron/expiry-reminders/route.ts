import { randomUUID } from "node:crypto";
import {
  FieldValue,
  Timestamp,
  type DocumentReference,
  type DocumentSnapshot,
  type QueryDocumentSnapshot
} from "firebase-admin/firestore";
import type { BatchResponse } from "firebase-admin/messaging";
import { getAdminDb, getAdminMessaging } from "@/lib/firebaseAdmin";
import { deleteDocumentRefs } from "@/lib/firestoreDelete";
import { ApiError, json, jsonError, requireCronSecret } from "@/lib/http";
import {
  CRON_LEASE_MS,
  DELIVERY_LEASE_MS,
  MAX_DELIVERY_ATTEMPTS,
  MAX_TOKENS_PER_USER,
  decideDelivery,
  isDueDelivery,
  notificationOutboxId,
  retryDelayMs
} from "@/lib/notificationDelivery";
import { notificationRetentionCutoff } from "@/lib/notificationObservability";
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

const OUTBOX_BATCH_SIZE = 200;
const OUTBOX_CONCURRENCY = 10;
const CLEANUP_BATCH_SIZE = 200;

type Summary = {
  runId: string;
  runDate: string;
  lease: "acquired" | "locked" | "completed";
  scanned: number;
  matched: number;
  enqueued: number;
  sent: number;
  sentDevices: number;
  deviceFailures: number;
  skipped: number;
  retried: number;
  deadLetters: number;
  backlog: number;
  cleanedOutbox: number;
  cleanedLogs: number;
  errors: string[];
};

type TokenDoc = {
  id: string;
  token: string;
};

type OutboxDelivery = {
  kind: "expiryReminder" | "dailyPushTest";
  roomId: string;
  couponId: string;
  uid: string;
  daysBefore: number;
  targetDate: string;
  title: string;
  body: string;
  deepLink: string;
  type: string;
  attempts: number;
};

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : String(error);
}

function errorCode(error: unknown) {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  const code = (error as { code?: unknown }).code;
  return typeof code === "string" ? code : undefined;
}

function isAlreadyExistsError(error: unknown) {
  if (typeof error !== "object" || error === null || !("code" in error)) return false;
  const code = (error as { code?: unknown }).code;
  return code === 6 || code === "6" || code === "already-exists";
}

async function acquireDailyLease(runDate: string, now: Date) {
  const db = getAdminDb();
  const ref = db.doc(`cronLeases/expiry-reminders-${runDate}`);
  const runId = randomUUID();
  const nowMs = now.getTime();

  const lease = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const status = snapshot.get("status");
    const leaseUntil = snapshot.get("leaseUntil");
    if (status === "completed") return "completed" as const;
    if (status === "running" && leaseUntil instanceof Timestamp && leaseUntil.toMillis() > nowMs) {
      return "locked" as const;
    }

    transaction.set(ref, {
      job: "expiry-reminders",
      runDate,
      runId,
      status: "running",
      startedAt: Timestamp.fromDate(now),
      leaseUntil: Timestamp.fromMillis(nowMs + CRON_LEASE_MS),
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
    return "acquired" as const;
  });

  return { ref, runId, lease };
}

async function finishDailyLease(
  ref: DocumentReference,
  runId: string,
  summary: Summary,
  status: "completed" | "partial" | "failed"
) {
  await getAdminDb().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("runId") !== runId) return;
    transaction.set(ref, {
      status,
      completedAt: FieldValue.serverTimestamp(),
      leaseUntil: FieldValue.delete(),
      summary: {
        scanned: summary.scanned,
        matched: summary.matched,
        enqueued: summary.enqueued,
        sent: summary.sent,
        sentDevices: summary.sentDevices,
        deviceFailures: summary.deviceFailures,
        skipped: summary.skipped,
        retried: summary.retried,
        deadLetters: summary.deadLetters,
        backlog: summary.backlog,
        cleanedOutbox: summary.cleanedOutbox,
        cleanedLogs: summary.cleanedLogs,
        errorCount: summary.errors.length
      },
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  });
}

async function tokenDocsForUid(uid: string): Promise<TokenDoc[]> {
  const tokens = await getAdminDb().collection(`users/${uid}/pushTokens`).limit(MAX_TOKENS_PER_USER).get();
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

  if (hasDeletes) await batch.commit();
}

async function enqueueDelivery(delivery: Omit<OutboxDelivery, "attempts">, now: Date) {
  const db = getAdminDb();
  const id = notificationOutboxId(delivery);
  try {
    await db.doc(`notificationOutbox/${id}`).create({
      ...delivery,
      status: "pending",
      attempts: 0,
      nextAttemptAt: Timestamp.fromDate(now),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });
    return true;
  } catch (error) {
    if (isAlreadyExistsError(error)) return false;
    throw error;
  }
}

function parseDelivery(snapshot: DocumentSnapshot): OutboxDelivery | null {
  const data = snapshot.data();
  if (!data) return null;
  const kind = data.kind;
  if (kind !== "expiryReminder" && kind !== "dailyPushTest") return null;
  const strings = ["roomId", "couponId", "uid", "targetDate", "title", "body", "deepLink", "type"] as const;
  if (strings.some((key) => typeof data[key] !== "string" || data[key].length === 0)) return null;
  if (!Number.isInteger(data.daysBefore) || !Number.isInteger(data.attempts)) return null;
  return {
    kind,
    roomId: data.roomId,
    couponId: data.couponId,
    uid: data.uid,
    daysBefore: data.daysBefore,
    targetDate: data.targetDate,
    title: data.title,
    body: data.body,
    deepLink: data.deepLink,
    type: data.type,
    attempts: data.attempts
  };
}

async function claimDelivery(snapshot: QueryDocumentSnapshot, runId: string, now: Date) {
  const db = getAdminDb();
  const nowMs = now.getTime();
  return db.runTransaction(async (transaction) => {
    const current = await transaction.get(snapshot.ref);
    const nextAttemptAt = current.get("nextAttemptAt");
    const leaseUntil = current.get("leaseUntil");
    if (!isDueDelivery({
      status: current.get("status"),
      nextAttemptAtMs: nextAttemptAt instanceof Timestamp ? nextAttemptAt.toMillis() : undefined,
      leaseUntilMs: leaseUntil instanceof Timestamp ? leaseUntil.toMillis() : undefined,
      nowMs
    })) return null;

    const delivery = parseDelivery(current);
    if (!delivery) {
      transaction.set(snapshot.ref, {
        status: "deadLetter",
        lastError: "알림 문서 형식이 올바르지 않습니다.",
        completedAt: FieldValue.serverTimestamp(),
        nextAttemptAt: FieldValue.delete(),
        leaseUntil: FieldValue.delete(),
        leaseOwner: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });
      return null;
    }

    const attempts = delivery.attempts + 1;
    transaction.set(snapshot.ref, {
      status: "sending",
      attempts,
      leaseOwner: runId,
      leaseUntil: Timestamp.fromMillis(nowMs + DELIVERY_LEASE_MS),
      nextAttemptAt: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
    return { ...delivery, attempts };
  });
}

async function completeDelivery(
  ref: DocumentReference,
  runId: string,
  fields: Record<string, unknown>
) {
  await getAdminDb().runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (snapshot.get("status") !== "sending" || snapshot.get("leaseOwner") !== runId) return;
    transaction.set(ref, {
      ...fields,
      leaseOwner: FieldValue.delete(),
      leaseUntil: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp()
    }, { merge: true });
  });
}

async function skipDelivery(ref: DocumentReference, runId: string, reason: string) {
  await completeDelivery(ref, runId, {
    status: "skipped",
    skipReason: reason,
    nextAttemptAt: FieldValue.delete(),
    completedAt: FieldValue.serverTimestamp()
  });
}

async function validateDelivery(delivery: OutboxDelivery) {
  const db = getAdminDb();
  const user = await db.doc(`users/${delivery.uid}`).get();
  if (!user.exists || user.get("pushEnabled") === false) return "push-disabled";
  if (!shouldNotify(delivery.daysBefore, user.data()) && delivery.kind === "expiryReminder") {
    return "notification-settings-changed";
  }

  const membership = await db.doc(`rooms/${delivery.roomId}/members/${delivery.uid}`).get();
  if (!membership.exists) return "not-a-room-member";
  if (delivery.kind === "dailyPushTest") return null;

  const coupon = await db.doc(`rooms/${delivery.roomId}/coupons/${delivery.couponId}`).get();
  if (!coupon.exists) return "coupon-deleted";
  if (coupon.get("status") !== "active" && coupon.get("status") !== "reserved") return "coupon-inactive";
  if (coupon.get("expiresLocalDate") !== delivery.targetDate) return "coupon-expiry-changed";
  const ownerUid = coupon.get("ownerUid");
  if (coupon.get("visibility") === "private" && ownerUid !== delivery.uid) return "coupon-private";
  if (coupon.get("notifyTarget") === "ownerOnly" && ownerUid !== delivery.uid) return "owner-only";
  return null;
}

async function writeLegacyLog(delivery: OutboxDelivery) {
  const logId = notificationLogId(
    delivery.roomId,
    delivery.couponId,
    delivery.daysBefore,
    delivery.targetDate
  );
  await getAdminDb().doc(`notificationLogs/${logId}`).set({
    roomId: delivery.roomId,
    couponId: delivery.couponId,
    daysBefore: delivery.daysBefore,
    targetDate: delivery.targetDate,
    sentToUids: FieldValue.arrayUnion(delivery.uid),
    kind: delivery.kind,
    sentAt: FieldValue.serverTimestamp()
  }, { merge: true });
}

async function processDelivery(
  snapshot: QueryDocumentSnapshot,
  runId: string,
  now: Date,
  summary: Summary
) {
  const delivery = await claimDelivery(snapshot, runId, now);
  if (!delivery) return;

  try {
    const invalidReason = await validateDelivery(delivery);
    if (invalidReason) {
      await skipDelivery(snapshot.ref, runId, invalidReason);
      summary.skipped += 1;
      return;
    }

    const tokenDocs = await tokenDocsForUid(delivery.uid);
    if (tokenDocs.length === 0) {
      await skipDelivery(snapshot.ref, runId, "no-push-token");
      summary.skipped += 1;
      return;
    }

    const response = await getAdminMessaging().sendEachForMulticast({
      tokens: tokenDocs.map((doc) => doc.token),
      notification: { title: delivery.title, body: delivery.body },
      data: {
        roomId: delivery.roomId,
        couponId: delivery.couponId,
        daysBefore: String(delivery.daysBefore),
        deepLink: delivery.deepLink,
        type: delivery.type
      },
      android: {
        priority: "high",
        notification: {
          channelId: "coupon_expiry",
          clickAction: "OPEN_COUPON_DETAIL",
          color: "#00B4A6",
          icon: "ic_stat_gd_notification",
          visibility: "private"
        }
      }
    });
    await removeInvalidTokens(delivery.uid, tokenDocs, response);

    const decision = decideDelivery(
      response.responses.map((item) => ({ success: item.success, errorCode: item.error?.code })),
      delivery.attempts
    );
    if (decision === "sent") {
      await completeDelivery(snapshot.ref, runId, {
        status: "sent",
        successCount: response.successCount,
        failureCount: response.failureCount,
        nextAttemptAt: FieldValue.delete(),
        sentAt: FieldValue.serverTimestamp(),
        completedAt: FieldValue.serverTimestamp()
      });
      summary.sent += 1;
      summary.sentDevices += response.successCount;
      summary.deviceFailures += response.failureCount;
      try {
        await writeLegacyLog(delivery);
      } catch (error) {
        summary.errors.push(`legacy-log:${snapshot.id}:${errorMessage(error)}`);
      }
      return;
    }

    if (decision === "retry") {
      const nextAttemptAt = new Date(now.getTime() + retryDelayMs(delivery.attempts));
      await completeDelivery(snapshot.ref, runId, {
        status: "retry",
        nextAttemptAt: Timestamp.fromDate(nextAttemptAt),
        lastError: response.responses.map((item) => item.error?.code).filter(Boolean).join(",").slice(0, 500)
      });
      summary.retried += 1;
      return;
    }

    await completeDelivery(snapshot.ref, runId, {
      status: decision,
      nextAttemptAt: FieldValue.delete(),
      lastError: response.responses.map((item) => item.error?.code).filter(Boolean).join(",").slice(0, 500),
      completedAt: FieldValue.serverTimestamp()
    });
    if (decision === "deadLetter") summary.deadLetters += 1;
    else summary.skipped += 1;
  } catch (error) {
    const terminal = delivery.attempts >= MAX_DELIVERY_ATTEMPTS;
    await completeDelivery(snapshot.ref, runId, {
      status: terminal ? "deadLetter" : "retry",
      nextAttemptAt: terminal
        ? FieldValue.delete()
        : Timestamp.fromMillis(now.getTime() + retryDelayMs(delivery.attempts)),
      lastError: `${errorCode(error) ?? "unknown"}:${errorMessage(error)}`.slice(0, 500),
      ...(terminal ? { completedAt: FieldValue.serverTimestamp() } : {})
    });
    if (terminal) summary.deadLetters += 1;
    else summary.retried += 1;
    summary.errors.push(`delivery:${snapshot.id}:${errorMessage(error)}`);
  }
}

async function processDueDeliveries(runId: string, now: Date, summary: Summary) {
  const db = getAdminDb();
  const due = await db.collection("notificationOutbox")
    .where("nextAttemptAt", "<=", Timestamp.fromDate(now))
    .orderBy("nextAttemptAt")
    .limit(OUTBOX_BATCH_SIZE)
    .get();
  const expired = await db.collection("notificationOutbox")
    .where("leaseUntil", "<=", Timestamp.fromDate(now))
    .orderBy("leaseUntil")
    .limit(OUTBOX_BATCH_SIZE)
    .get();
  const deliveries = new Map<string, QueryDocumentSnapshot>();
  [...due.docs, ...expired.docs].forEach((doc) => deliveries.set(doc.id, doc));
  const values = [...deliveries.values()];
  for (let index = 0; index < values.length; index += OUTBOX_CONCURRENCY) {
    await Promise.all(
      values.slice(index, index + OUTBOX_CONCURRENCY)
        .map((delivery) => processDelivery(delivery, runId, now, summary))
    );
  }

  const [remainingDue, remainingExpired] = await Promise.all([
    db.collection("notificationOutbox")
      .where("nextAttemptAt", "<=", Timestamp.fromDate(now))
      .limit(1)
      .get(),
    db.collection("notificationOutbox")
      .where("leaseUntil", "<=", Timestamp.fromDate(now))
      .limit(1)
      .get()
  ]);
  summary.backlog = remainingDue.size + remainingExpired.size;
}

async function cleanupNotificationHistory(now: Date, summary: Summary) {
  const db = getAdminDb();
  const cutoff = Timestamp.fromDate(notificationRetentionCutoff(now));
  const [outbox, logs] = await Promise.all([
    db.collection("notificationOutbox")
      .where("completedAt", "<", cutoff)
      .limit(CLEANUP_BATCH_SIZE)
      .get(),
    db.collection("notificationLogs")
      .where("sentAt", "<", cutoff)
      .limit(CLEANUP_BATCH_SIZE)
      .get()
  ]);
  await deleteDocumentRefs(db, [
    ...outbox.docs.map((doc) => doc.ref),
    ...logs.docs.map((doc) => doc.ref)
  ]);
  summary.cleanedOutbox = outbox.size;
  summary.cleanedLogs = logs.size;
}

async function legacySentUids(roomId: string, couponId: string, daysBefore: number, targetDate: string) {
  const id = notificationLogId(roomId, couponId, daysBefore, targetDate);
  const snapshot = await getAdminDb().doc(`notificationLogs/${id}`).get();
  const values = snapshot.get("sentToUids");
  return new Set(
    Array.isArray(values) ? values.filter((value): value is string => typeof value === "string") : []
  );
}

async function enqueueDailyPushTest(today: string, now: Date, summary: Summary) {
  const db = getAdminDb();
  const roomRef = await ensurePushTestRoom();
  const members = await roomRef.collection("members").get();
  const alreadySent = await legacySentUids(PUSH_TEST_ROOM_ID, "daily-push-test", 0, today);
  for (const member of members.docs) {
    const uid = member.id;
    if (alreadySent.has(uid)) {
      summary.skipped += 1;
      continue;
    }
    const user = await db.doc(`users/${uid}`).get();
    if (!user.exists || user.get("pushEnabled") === false) {
      summary.skipped += 1;
      continue;
    }
    summary.matched += 1;
    if (await enqueueDelivery({
      kind: "dailyPushTest",
      roomId: PUSH_TEST_ROOM_ID,
      couponId: "daily-push-test",
      uid,
      daysBefore: 0,
      targetDate: today,
      title: "푸시 상태 확인",
      body: "알림 연결이 정상이에요.",
      deepLink: `giftcondoctor://rooms/${PUSH_TEST_ROOM_ID}`,
      type: "daily_push_test"
    }, now)) summary.enqueued += 1;
    else summary.skipped += 1;
  }
}

async function enqueueExpiryReminders(now: Date, summary: Summary) {
  const db = getAdminDb();
  const today = seoulLocalDate(now);
  const dates = targetDates(today);
  const targetDateValues = dates.map((item) => item.targetDate);
  const couponSnapshots = await Promise.all(
    targetDateValues.map((targetDate) =>
      db.collectionGroup("coupons").where("expiresLocalDate", "==", targetDate).get()
    )
  );
  const couponDocs = couponSnapshots.flatMap((snapshot) => snapshot.docs);
  summary.scanned = couponDocs.length;

  for (const coupon of couponDocs) {
    const roomRef = coupon.ref.parent.parent;
    const couponData = coupon.data();
    if (!roomRef || (couponData.status !== "active" && couponData.status !== "reserved")) {
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
    const ownerUid = couponData.ownerUid;
    if (typeof ownerUid !== "string") {
      summary.skipped += 1;
      continue;
    }

    const roomId = roomRef.id;
    const couponId = coupon.id;
    const members = await roomRef.collection("members").get();
    const visibility = couponData.visibility ?? "room";
    const notifyTarget = couponData.notifyTarget ?? "allMembers";
    const targets = members.docs.filter((member) => {
      if (visibility === "private" || notifyTarget === "ownerOnly") return member.id === ownerUid;
      return true;
    });
    const alreadySent = await legacySentUids(roomId, couponId, daysBefore, expiresLocalDate);

    for (const member of targets) {
      const uid = member.id;
      if (alreadySent.has(uid)) {
        summary.skipped += 1;
        continue;
      }
      const user = await db.doc(`users/${uid}`).get();
      if (!user.exists || user.get("pushEnabled") === false || !shouldNotify(daysBefore, user.data())) {
        summary.skipped += 1;
        continue;
      }
      summary.matched += 1;
      if (await enqueueDelivery({
        kind: "expiryReminder",
        roomId,
        couponId,
        uid,
        daysBefore,
        targetDate: expiresLocalDate,
        title: notificationTitle(couponData.brand, daysBefore),
        body: notificationBody(couponData.title, expiresLocalDate, daysBefore),
        deepLink: `giftcondoctor://rooms/${roomId}/coupons/${couponId}`,
        type: "expiry_reminder"
      }, now)) summary.enqueued += 1;
      else summary.skipped += 1;
    }
  }
}

async function runExpiryReminders(now = new Date()) {
  const runDate = seoulLocalDate(now);
  const lease = await acquireDailyLease(runDate, now);
  const summary: Summary = {
    runId: lease.runId,
    runDate,
    lease: lease.lease,
    scanned: 0,
    matched: 0,
    enqueued: 0,
    sent: 0,
    sentDevices: 0,
    deviceFailures: 0,
    skipped: 0,
    retried: 0,
    deadLetters: 0,
    backlog: 0,
    cleanedOutbox: 0,
    cleanedLogs: 0,
    errors: []
  };
  if (lease.lease !== "acquired") return summary;

  try {
    await enqueueExpiryReminders(now, summary);
    await enqueueDailyPushTest(runDate, now, summary);
    await processDueDeliveries(lease.runId, now, summary);
    await cleanupNotificationHistory(now, summary);
    await finishDailyLease(
      lease.ref,
      lease.runId,
      summary,
      summary.errors.length > 0 || summary.retried > 0 || summary.deadLetters > 0 || summary.backlog > 0
        ? "partial"
        : "completed"
    );
    return summary;
  } catch (error) {
    summary.errors.push(errorMessage(error));
    await finishDailyLease(lease.ref, lease.runId, summary, "failed");
    throw Object.assign(error instanceof Error ? error : new Error(String(error)), { summary });
  }
}

async function handle(request: Request) {
  try {
    requireCronSecret(request);
    const summary = await runExpiryReminders();
    if (summary.errors.length > 0 || summary.retried > 0 || summary.deadLetters > 0 || summary.backlog > 0) {
      console.error("expiry-reminders completed partially", summary);
      return json(summary, { status: 500 });
    }
    console.info("expiry-reminders completed", summary);
    return json(summary);
  } catch (error) {
    if (error instanceof ApiError) return jsonError(error);
    const summary = typeof error === "object" && error !== null && "summary" in error
      ? (error as { summary: Summary }).summary
      : undefined;
    if (summary) return json(summary, { status: 500 });
    return jsonError(error);
  }
}

export async function GET(request: Request) {
  return handle(request);
}

export async function POST(request: Request) {
  return handle(request);
}
