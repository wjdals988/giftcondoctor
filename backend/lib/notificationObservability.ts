export const NOTIFICATION_RETENTION_DAYS = 30;

export type NotificationHealth = "healthy" | "warning" | "critical" | "unknown";

export type NotificationMetrics = {
  pending: number;
  sending: number;
  retry: number;
  sent: number;
  skipped: number;
  deadLetter: number;
  due: number;
  staleSending: number;
  lastRunStatus?: string;
};

export function notificationHealth(metrics: NotificationMetrics): NotificationHealth {
  if (metrics.deadLetter > 0 || metrics.staleSending > 0) return "critical";
  if (metrics.retry > 0 || metrics.due > 0 || metrics.lastRunStatus === "partial" || metrics.lastRunStatus === "failed") {
    return "warning";
  }
  if (!metrics.lastRunStatus) return "unknown";
  return "healthy";
}

export function ageSeconds(date: Date | undefined, now = new Date()) {
  if (!date) return null;
  return Math.max(0, Math.floor((now.getTime() - date.getTime()) / 1_000));
}

export function notificationRetentionCutoff(now = new Date()) {
  return new Date(now.getTime() - NOTIFICATION_RETENTION_DAYS * 24 * 60 * 60 * 1_000);
}
