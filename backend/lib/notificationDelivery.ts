import { createHash } from "node:crypto";

export const CRON_LEASE_MS = 6 * 60 * 1_000;
export const DELIVERY_LEASE_MS = 2 * 60 * 1_000;
export const MAX_DELIVERY_ATTEMPTS = 5;

export type DeliveryStatus =
  | "pending"
  | "sending"
  | "retry"
  | "sent"
  | "skipped"
  | "deadLetter";

export type DeliveryDecision = "sent" | "retry" | "skipped" | "deadLetter";

export type DeliveryResponse = {
  success: boolean;
  errorCode?: string;
};

export function notificationOutboxId(params: {
  kind: string;
  roomId: string;
  couponId: string;
  daysBefore: number;
  targetDate: string;
  uid: string;
}) {
  const identity = [
    params.kind,
    params.roomId,
    params.couponId,
    String(params.daysBefore),
    params.targetDate,
    params.uid
  ].join("\u001f");
  return createHash("sha256").update(identity).digest("hex");
}

export function isRetryableFcmCode(code: string | undefined) {
  return (
    code === "messaging/server-unavailable" ||
    code === "messaging/internal-error" ||
    code === "messaging/quota-exceeded" ||
    code === "messaging/message-rate-exceeded" ||
    code === "messaging/device-message-rate-exceeded" ||
    code === "messaging/topics-message-rate-exceeded" ||
    code === "messaging/unknown-error"
  );
}

export function retryDelayMs(attempt: number, random = Math.random) {
  const normalizedAttempt = Math.max(1, Math.floor(attempt));
  const base = Math.min(6 * 60 * 60 * 1_000, 60_000 * (2 ** (normalizedAttempt - 1)));
  const jitter = 0.8 + Math.min(1, Math.max(0, random())) * 0.4;
  return Math.round(base * jitter);
}

export function decideDelivery(
  responses: DeliveryResponse[],
  attempt: number
): DeliveryDecision {
  if (responses.some((response) => response.success)) return "sent";
  if (responses.length === 0) return "skipped";
  if (responses.some((response) => isRetryableFcmCode(response.errorCode))) {
    return attempt >= MAX_DELIVERY_ATTEMPTS ? "deadLetter" : "retry";
  }
  return "skipped";
}

export function isDueDelivery(params: {
  status: unknown;
  nextAttemptAtMs?: number;
  leaseUntilMs?: number;
  nowMs: number;
}) {
  if (params.status === "pending" || params.status === "retry") {
    return (params.nextAttemptAtMs ?? 0) <= params.nowMs;
  }
  return params.status === "sending" && (params.leaseUntilMs ?? 0) <= params.nowMs;
}
