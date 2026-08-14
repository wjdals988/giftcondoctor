import { FieldPath, Timestamp } from "firebase-admin/firestore";
import {
  blobCleanupHealth,
  type BlobCleanupMetrics
} from "@/lib/blobCleanupQueue";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, requireCronSecret } from "@/lib/http";
import {
  ageSeconds,
  notificationHealth,
  type NotificationMetrics
} from "@/lib/notificationObservability";

export const runtime = "nodejs";

const STATUSES = ["pending", "sending", "retry", "sent", "skipped", "deadLetter"] as const;
const BLOB_CLEANUP_STATUSES = ["pending", "deleting", "retry", "deleted", "deadLetter"] as const;

export async function GET(request: Request) {
  try {
    requireCronSecret(request);
    const db = getAdminDb();
    const now = new Date();
    const nowTimestamp = Timestamp.fromDate(now);

    const [
      statusSnapshots,
      dueSnapshot,
      staleSnapshot,
      oldestDueSnapshot,
      latestRunSnapshot,
      blobCleanupStatusSnapshots,
      blobCleanupDueSnapshot,
      blobCleanupStaleSnapshot
    ] = await Promise.all([
      Promise.all(
        STATUSES.map((status) => db.collection("notificationOutbox").where("status", "==", status).count().get())
      ),
      db.collection("notificationOutbox").where("nextAttemptAt", "<=", nowTimestamp).count().get(),
      db.collection("notificationOutbox").where("leaseUntil", "<=", nowTimestamp).count().get(),
      db.collection("notificationOutbox").where("nextAttemptAt", "<=", nowTimestamp)
        .orderBy("nextAttemptAt").limit(1).get(),
      db.collection("cronLeases").orderBy(FieldPath.documentId(), "desc").limit(1).get(),
      Promise.all(
        BLOB_CLEANUP_STATUSES.map((status) =>
          db.collection("blobCleanupQueue").where("status", "==", status).count().get()
        )
      ),
      db.collection("blobCleanupQueue").where("nextAttemptAt", "<=", nowTimestamp).count().get(),
      db.collection("blobCleanupQueue").where("leaseUntil", "<=", nowTimestamp).count().get()
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
    const notificationState = notificationHealth(metrics);
    const blobCleanupCounts = Object.fromEntries(
      BLOB_CLEANUP_STATUSES.map((status, index) => [status, blobCleanupStatusSnapshots[index].data().count])
    ) as Record<typeof BLOB_CLEANUP_STATUSES[number], number>;
    const blobCleanupMetrics: BlobCleanupMetrics = {
      ...blobCleanupCounts,
      due: blobCleanupDueSnapshot.data().count,
      staleDeleting: blobCleanupStaleSnapshot.data().count
    };
    const blobCleanupState = blobCleanupHealth(blobCleanupMetrics);
    const health = notificationState === "critical" || blobCleanupState === "critical"
      ? "critical"
      : notificationState === "warning" || blobCleanupState === "warning"
        ? "warning"
        : "healthy";

    return json({
      ok: health === "healthy",
      health,
      metrics,
      blobCleanup: {
        health: blobCleanupState,
        metrics: blobCleanupMetrics
      },
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
