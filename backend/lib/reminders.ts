export const SEOUL_TIME_ZONE = "Asia/Seoul";
export const REMINDER_SCAN_DAYS = [7, 5, 3, 2, 1, 0] as const;

export type NotificationMode = "minimal" | "basic" | "careful";

export const NOTIFICATION_MODE_DAYS: Record<NotificationMode, number[]> = {
  minimal: [3, 0],
  basic: [7, 3, 1, 0],
  careful: [7, 5, 3, 2, 1, 0]
};

export function normalizeDays(days: unknown): number[] {
  if (!Array.isArray(days)) return [];
  return [...new Set(days.filter((value) => Number.isInteger(value) && value >= 0 && value <= 30))]
    .sort((a, b) => b - a);
}

export function daysForMode(mode: unknown): number[] {
  if (mode === "minimal" || mode === "basic" || mode === "careful") {
    return NOTIFICATION_MODE_DAYS[mode];
  }
  return NOTIFICATION_MODE_DAYS.basic;
}

export function seoulLocalDate(now = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: SEOUL_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).format(now);
}

export function addLocalDays(localDate: string, days: number) {
  const [year, month, day] = localDate.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return date.toISOString().slice(0, 10);
}

export function daysBetweenLocalDates(from: string, to: string) {
  const [fy, fm, fd] = from.split("-").map(Number);
  const [ty, tm, td] = to.split("-").map(Number);
  const fromTime = Date.UTC(fy, fm - 1, fd);
  const toTime = Date.UTC(ty, tm - 1, td);
  return Math.round((toTime - fromTime) / 86_400_000);
}

export function targetDates(today = seoulLocalDate()) {
  return REMINDER_SCAN_DAYS.map((daysBefore) => ({
    daysBefore,
    targetDate: addLocalDays(today, daysBefore)
  }));
}

type SettingSource = {
  notificationDays?: unknown;
  notificationMode?: unknown;
  defaultNotificationDays?: unknown;
  defaultNotificationMode?: unknown;
};

export function resolveReminderDays(user?: SettingSource, room?: SettingSource, member?: SettingSource) {
  const userDays = normalizeDays(user?.defaultNotificationDays);
  if (userDays.length > 0) return userDays;
  if (user?.defaultNotificationMode) return daysForMode(user.defaultNotificationMode);

  return NOTIFICATION_MODE_DAYS.basic;
}

export function shouldNotify(daysBefore: number, user?: SettingSource, room?: SettingSource, member?: SettingSource) {
  return resolveReminderDays(user, room, member).includes(daysBefore);
}

export function notificationTitle(brand: unknown, daysBefore: number) {
  const brandText = typeof brand === "string" && brand.trim().length > 0 ? brand.trim() : "";

  if (daysBefore === 0) {
    return "오늘 만료되는 쿠폰이 있어요";
  }
  if (daysBefore === 1) {
    return brandText ? `${brandText} 쿠폰이 내일 만료돼요` : "내일 만료되는 쿠폰이 있어요";
  }
  return `${daysBefore}일 뒤 만료되는 쿠폰이 있어요`;
}

export function notificationBody(title: unknown, expiresLocalDate: unknown, daysBefore?: number) {
  const couponTitle = typeof title === "string" && title.trim().length > 0 ? title.trim() : "쿠폰";
  const date = typeof expiresLocalDate === "string" ? expiresLocalDate : "";
  if (daysBefore === 0) return `${couponTitle} · 오늘까지 사용할 수 있어요.`;
  if (daysBefore === 1) return `${couponTitle} · 내일까지 사용하세요.`;
  if (typeof daysBefore === "number" && daysBefore > 1) return `${couponTitle} · ${daysBefore}일 남았어요.`;
  return date ? `${couponTitle} · ${date}` : couponTitle;
}

export function notificationLogId(roomId: string, couponId: string, daysBefore: number, targetDate: string) {
  return `${roomId}_${couponId}_${daysBefore}_${targetDate}`;
}

export function isInvalidFcmTokenCode(code: string | undefined) {
  return (
    code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token" ||
    code === "messaging/invalid-argument"
  );
}
