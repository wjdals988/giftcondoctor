import { describe, expect, it } from "vitest";
import {
  ageSeconds,
  notificationHealth,
  notificationRetentionCutoff,
  type NotificationMetrics
} from "../lib/notificationObservability";

const healthy: NotificationMetrics = {
  pending: 0,
  sending: 0,
  retry: 0,
  sent: 10,
  skipped: 2,
  deadLetter: 0,
  due: 0,
  staleSending: 0,
  lastRunStatus: "completed"
};

describe("notification operational health", () => {
  it("reports healthy only after a completed run without actionable work", () => {
    expect(notificationHealth(healthy)).toBe("healthy");
    expect(notificationHealth({ ...healthy, lastRunStatus: undefined })).toBe("unknown");
  });

  it("reports warning for retry, due work, or a partial run", () => {
    expect(notificationHealth({ ...healthy, retry: 1 })).toBe("warning");
    expect(notificationHealth({ ...healthy, due: 1 })).toBe("warning");
    expect(notificationHealth({ ...healthy, lastRunStatus: "partial" })).toBe("warning");
  });

  it("prioritizes dead letters and expired send leases as critical", () => {
    expect(notificationHealth({ ...healthy, deadLetter: 1 })).toBe("critical");
    expect(notificationHealth({ ...healthy, staleSending: 1, retry: 3 })).toBe("critical");
  });

  it("returns a non-negative whole-second age", () => {
    const now = new Date("2026-08-15T00:00:10.900Z");
    expect(ageSeconds(new Date("2026-08-15T00:00:00.100Z"), now)).toBe(10);
    expect(ageSeconds(new Date("2026-08-15T00:00:11.000Z"), now)).toBe(0);
    expect(ageSeconds(undefined, now)).toBeNull();
  });

  it("keeps exactly thirty days of terminal notification history", () => {
    expect(notificationRetentionCutoff(new Date("2026-08-31T09:00:00.000Z")).toISOString())
      .toBe("2026-08-01T09:00:00.000Z");
  });
});
