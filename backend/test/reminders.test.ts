import { describe, expect, it } from "vitest";
import {
  addLocalDays,
  daysBetweenLocalDates,
  isInvalidFcmTokenCode,
  notificationBody,
  notificationLogId,
  notificationTitle,
  resolveReminderDays,
  seoulLocalDate,
  shouldNotify,
  targetDates
} from "../lib/reminders";

describe("reminder date logic", () => {
  it("uses Asia/Seoul when converting the current date", () => {
    expect(seoulLocalDate(new Date("2026-05-15T15:01:00.000Z"))).toBe("2026-05-16");
  });

  it("builds target dates for the full scan window", () => {
    expect(targetDates("2026-05-16")).toEqual([
      { daysBefore: 7, targetDate: "2026-05-23" },
      { daysBefore: 5, targetDate: "2026-05-21" },
      { daysBefore: 3, targetDate: "2026-05-19" },
      { daysBefore: 2, targetDate: "2026-05-18" },
      { daysBefore: 1, targetDate: "2026-05-17" },
      { daysBefore: 0, targetDate: "2026-05-16" }
    ]);
  });

  it("calculates local date offsets independent of host timezone", () => {
    expect(addLocalDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(daysBetweenLocalDates("2026-05-16", "2026-05-23")).toBe(7);
  });
});

describe("reminder settings", () => {
  it("uses personal global settings only", () => {
    expect(resolveReminderDays({ defaultNotificationMode: "careful" })).toEqual([7, 5, 3, 2, 1, 0]);
    expect(resolveReminderDays(
      { defaultNotificationMode: "careful" },
      { defaultNotificationMode: "minimal" }
    )).toEqual([7, 5, 3, 2, 1, 0]);
    expect(resolveReminderDays(
      { defaultNotificationMode: "careful" },
      { defaultNotificationMode: "minimal" },
      { notificationDays: [7, 1, 0] }
    )).toEqual([7, 5, 3, 2, 1, 0]);
  });

  it("matches days from the personal mode", () => {
    expect(shouldNotify(3, { defaultNotificationMode: "minimal" })).toBe(true);
    expect(shouldNotify(7, { defaultNotificationMode: "minimal" })).toBe(false);
  });

  it("ignores unsupported legacy custom days that the cron never scans", () => {
    expect(resolveReminderDays({ defaultNotificationDays: [30, 10] })).toEqual([7, 3, 1, 0]);
    expect(resolveReminderDays({ defaultNotificationDays: [7, 1, 0] })).toEqual([7, 1, 0]);
  });
});

describe("notification payload helpers", () => {
  it("uses Korean titles for D-day, tomorrow, and future reminders", () => {
    expect(notificationTitle("스타벅스", 0)).toBe("쿠폰 만료 D-Day");
    expect(notificationTitle("스타벅스", 1)).toBe("쿠폰 만료 D-1");
    expect(notificationTitle("", 7)).toBe("쿠폰 만료 D-7");
  });

  it("uses friendly reminder bodies", () => {
    expect(notificationBody("아메리카노 Tall", "2026-05-17", 0)).toBe("아메리카노 Tall · 오늘까지");
    expect(notificationBody("아메리카노 Tall", "2026-05-18", 1)).toBe("아메리카노 Tall · 내일까지");
    expect(notificationBody("아메리카노 Tall", "2026-05-24", 7)).toBe("아메리카노 Tall · 7일 남음");
  });

  it("builds deterministic log ids", () => {
    expect(notificationLogId("room1", "coupon1", 7, "2026-05-23")).toBe("room1_coupon1_7_2026-05-23");
  });

  it("detects invalid FCM token error codes", () => {
    expect(isInvalidFcmTokenCode("messaging/registration-token-not-registered")).toBe(true);
    expect(isInvalidFcmTokenCode("messaging/invalid-registration-token")).toBe(true);
    expect(isInvalidFcmTokenCode("messaging/internal-error")).toBe(false);
  });
});
