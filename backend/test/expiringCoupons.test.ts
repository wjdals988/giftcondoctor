import { describe, expect, it } from "vitest";
import {
  EXPIRING_SOON_DEFAULT_DAYS,
  EXPIRING_SOON_MAX_DAYS,
  daysBetween,
  parseExpiringDays,
  selectExpiringCoupons,
  type RawCoupon
} from "../lib/expiringCoupons";

const UID = "user-1";
const TODAY = "2026-08-28";

function coupon(overrides: Partial<RawCoupon> = {}): RawCoupon {
  return {
    roomId: "room-1",
    roomName: "내 쿠폰",
    couponId: "c1",
    title: "아메리카노",
    brand: "투썸",
    status: "active",
    visibility: "room",
    ownerUid: UID,
    expiresLocalDate: "2026-08-30",
    ...overrides
  };
}

describe("parseExpiringDays", () => {
  it("빈 값이면 기본 7일", () => {
    expect(parseExpiringDays(null)).toBe(EXPIRING_SOON_DEFAULT_DAYS);
    expect(parseExpiringDays("")).toBe(EXPIRING_SOON_DEFAULT_DAYS);
  });

  it("상한을 넘으면 잘라낸다", () => {
    expect(parseExpiringDays("9999")).toBe(EXPIRING_SOON_MAX_DAYS);
  });

  it("정수가 아니거나 음수면 거부한다", () => {
    expect(() => parseExpiringDays("-1")).toThrow();
    expect(() => parseExpiringDays("3.5")).toThrow();
    expect(() => parseExpiringDays("abc")).toThrow();
  });
});

describe("daysBetween", () => {
  it("날짜 차이를 일 단위로 센다", () => {
    expect(daysBetween(TODAY, "2026-08-28")).toBe(0);
    expect(daysBetween(TODAY, "2026-08-31")).toBe(3);
    expect(daysBetween(TODAY, "2026-08-27")).toBe(-1);
  });

  it("월·연 경계를 넘어도 정확하다", () => {
    expect(daysBetween("2026-12-31", "2027-01-01")).toBe(1);
    expect(daysBetween("2026-02-28", "2026-03-01")).toBe(1);
  });

  it("형식이 어긋나면 null", () => {
    expect(daysBetween(TODAY, "2026/08/30")).toBeNull();
    expect(daysBetween(TODAY, "")).toBeNull();
  });
});

describe("selectExpiringCoupons", () => {
  it("남의 비공개 쿠폰은 제외하고 내 비공개는 포함한다", () => {
    const raw = [
      coupon({ couponId: "mine", visibility: "private", ownerUid: UID }),
      coupon({ couponId: "theirs", visibility: "private", ownerUid: "other" })
    ];
    const { coupons } = selectExpiringCoupons(raw, UID, TODAY, 7);
    expect(coupons.map((c) => c.couponId)).toEqual(["mine"]);
  });

  it("종료 상태를 제외한다", () => {
    const raw = ["used", "expired", "deleted", "purging"].map((status, i) =>
      coupon({ couponId: `c${i}`, status })
    );
    expect(selectExpiringCoupons(raw, UID, TODAY, 7).coupons).toHaveLength(0);
  });

  it("status가 active로 남아 있어도 날짜가 지났으면 제외한다", () => {
    // 만료 상태 갱신 배치가 아직 없어 실제로 발생할 수 있는 조합이다.
    const raw = [coupon({ status: "active", expiresLocalDate: "2026-08-01" })];
    expect(selectExpiringCoupons(raw, UID, TODAY, 7).coupons).toHaveLength(0);
  });

  it("경계일을 포함하고 그 다음 날은 제외한다", () => {
    const raw = [
      coupon({ couponId: "in", expiresLocalDate: "2026-09-04" }),
      coupon({ couponId: "out", expiresLocalDate: "2026-09-05" })
    ];
    const { coupons } = selectExpiringCoupons(raw, UID, TODAY, 7);
    expect(coupons.map((c) => c.couponId)).toEqual(["in"]);
  });

  it("급한 순으로 정렬하고 같은 날은 이름순으로 안정 정렬한다", () => {
    const raw = [
      coupon({ couponId: "c3", title: "나중", expiresLocalDate: "2026-09-01" }),
      coupon({ couponId: "c2", title: "하마", expiresLocalDate: "2026-08-28" }),
      coupon({ couponId: "c1", title: "가나", expiresLocalDate: "2026-08-28" })
    ];
    const { coupons } = selectExpiringCoupons(raw, UID, TODAY, 7);
    expect(coupons.map((c) => c.couponId)).toEqual(["c1", "c2", "c3"]);
    expect(coupons[0].daysLeft).toBe(0);
  });

  it("제목이 비어 있으면 대체 문구를 넣는다", () => {
    const raw = [coupon({ title: "   " })];
    expect(selectExpiringCoupons(raw, UID, TODAY, 7).coupons[0].title).toBe("이름 없는 쿠폰");
  });

  it("limit을 넘으면 잘라내고 잘렸음을 알린다", () => {
    const raw = Array.from({ length: 5 }, (_, i) =>
      coupon({ couponId: `c${i}`, title: `쿠폰${i}` })
    );
    const result = selectExpiringCoupons(raw, UID, TODAY, 7, 3);
    expect(result.coupons).toHaveLength(3);
    expect(result.truncated).toBe(true);
  });

  it("limit 이내면 잘리지 않았다고 알린다", () => {
    const raw = [coupon()];
    expect(selectExpiringCoupons(raw, UID, TODAY, 7, 3).truncated).toBe(false);
  });
});
