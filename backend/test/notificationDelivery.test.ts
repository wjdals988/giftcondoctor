import { describe, expect, it } from "vitest";
import {
  MAX_DELIVERY_ATTEMPTS,
  decideDelivery,
  isDueDelivery,
  isRetryableFcmCode,
  notificationOutboxId,
  retryDelayMs
} from "../lib/notificationDelivery";

describe("notification outbox identity", () => {
  const delivery = {
    kind: "expiryReminder",
    roomId: "room-1",
    couponId: "coupon-1",
    daysBefore: 3,
    targetDate: "2026-08-18",
    uid: "member-1"
  };

  it("uses a deterministic opaque id for one recipient event", () => {
    const first = notificationOutboxId(delivery);
    expect(first).toBe(notificationOutboxId(delivery));
    expect(first).toMatch(/^[a-f0-9]{64}$/);
    expect(first).not.toContain("member-1");
  });

  it("changes the id when the recipient or target date changes", () => {
    expect(notificationOutboxId(delivery)).not.toBe(notificationOutboxId({
      ...delivery,
      uid: "member-2"
    }));
    expect(notificationOutboxId(delivery)).not.toBe(notificationOutboxId({
      ...delivery,
      targetDate: "2026-08-19"
    }));
  });
});

describe("notification retry policy", () => {
  it("retries transient FCM failures and not invalid tokens", () => {
    expect(isRetryableFcmCode("messaging/server-unavailable")).toBe(true);
    expect(isRetryableFcmCode("messaging/internal-error")).toBe(true);
    expect(isRetryableFcmCode("messaging/quota-exceeded")).toBe(true);
    expect(isRetryableFcmCode("messaging/registration-token-not-registered")).toBe(false);
  });

  it("uses exponential delays capped at six hours with bounded jitter", () => {
    expect(retryDelayMs(1, () => 0.5)).toBe(60_000);
    expect(retryDelayMs(2, () => 0.5)).toBe(120_000);
    expect(retryDelayMs(20, () => 0.5)).toBe(21_600_000);
    expect(retryDelayMs(1, () => 0)).toBe(48_000);
    expect(retryDelayMs(1, () => 1)).toBe(72_000);
  });

  it("does not retry a recipient after any device succeeds", () => {
    expect(decideDelivery([
      { success: true },
      { success: false, errorCode: "messaging/server-unavailable" }
    ], 1)).toBe("sent");
  });

  it("retries only all-failed transient deliveries and eventually dead-letters them", () => {
    const transient = [{ success: false, errorCode: "messaging/internal-error" }];
    expect(decideDelivery(transient, 1)).toBe("retry");
    expect(decideDelivery(transient, MAX_DELIVERY_ATTEMPTS)).toBe("deadLetter");
    expect(decideDelivery([
      { success: false, errorCode: "messaging/registration-token-not-registered" }
    ], 1)).toBe("skipped");
  });
});

describe("notification leases", () => {
  it("claims pending or retry work only after its due time", () => {
    expect(isDueDelivery({ status: "pending", nextAttemptAtMs: 1_000, nowMs: 1_000 })).toBe(true);
    expect(isDueDelivery({ status: "retry", nextAttemptAtMs: 1_001, nowMs: 1_000 })).toBe(false);
  });

  it("reclaims an expired send lease but not an active lease", () => {
    expect(isDueDelivery({ status: "sending", leaseUntilMs: 999, nowMs: 1_000 })).toBe(true);
    expect(isDueDelivery({ status: "sending", leaseUntilMs: 1_001, nowMs: 1_000 })).toBe(false);
    expect(isDueDelivery({ status: "sent", nowMs: 1_000 })).toBe(false);
  });
});
