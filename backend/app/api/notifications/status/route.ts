import { FieldPath, Timestamp } from "firebase-admin/firestore";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, requireCronSecret } from "@/lib/http";
import {
  ageSeconds,
  notificationHealth,
  type NotificationMetrics
} from "@/lib/notificationObservability";

export const runtime = "nodejs";

const STATUSES = ["pending", "sending", "retry", "sent", "skipped", "deadLetter"] as const;

export async function GET(request: Request) {
  try {
    requireCronSecret(request);
    const db = getAdminDb();
    const now = new Date();
    const nowTimestamp = Timestamp.fromDate(now);

    const [statusSnapshots, dueSnapshot, staleSnapshot, oldestDueSnapshot, latestRunSnapshot] = await Promise.all([
      Promise.all(
        STATUSES.map((status) => db.collection("notificationOutbox").where("status", "==", status).count().get())
      ),
      db.collection("notificationOutbox").where("nextAttemptAt", "<=", nowTimestamp).count().get(),
      db.collection("notificationOutbox").where("leaseUntil", "<=", nowTimestamp).count().get(),
      db.collection("notificationOutbox").where("nextAttemptAt", "<=", nowTimestamp)
        .orderBy("nextAttemptAt").limit(1).get(),
      db.collection("cronLeases").orderBy(FieldPath.documentId(), "desc").limit(1).get()
    ]);

    const counts = Object.fromEntries(
      STATUSES.map((status, index) => [status, statusSnapshots[index].data().count])
    ) as Record<typeof STATUSES[number], number>;
    const latestRun = latestRunSnapshot.docs[0];
    const oldestDue = oldestDueSnapshot.docs[0]?.get("nextAttemptAt");
    const metrics: NotificationMetrics = {
      ...counts,
      due: dueSnapshot.data().count,
      staleSending: staleSnapshot.data().count,
      lastRunStatus: typeof latestRun?.get("status") === "string" ? latestRun.get("status") : undefined
    };
    const health = notificationHealth(metrics);

    return json({
      ok: health === "healthy",
      health,
      metrics,
      oldestDueAgeSeconds: oldestDue instanceof Timestamp
        ? ageSeconds(oldestDue.toDate(), now)
        : null,
      lastRun: latestRun
        ? {
            id: latestRun.id,
            runDate: latestRun.get("runDate") ?? null,
            status: latestRun.get("status") ?? null,
            summary: latestRun.get("summary") ?? null
          }
        : null,
      timestamp: now.toISOString()
    }, {
      headers: { "Cache-Control": "no-store" }
    });
  } catch (error) {
    if (error instanceof ApiError) return jsonError(error);
    return jsonError(error);
  }
}
