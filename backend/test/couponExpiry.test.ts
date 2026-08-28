import { describe, expect, it } from "vitest";
import { selectCouponsToExpire, shouldExpireCoupon } from "../lib/couponExpiry";

const TODAY = "2026-08-28";

describe("shouldExpireCoupon", () => {
  it("만료일이 지난 active 쿠폰을 전이 대상으로 본다", () => {
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2026-08-27" }, TODAY)).toBe(true);
  });

  it("예약 상태도 전이시킨다", () => {
    // 예약해 두고 쓰지 않은 채 만료된 경우다. 예약이 만료를 막지는 않는다.
    expect(shouldExpireCoupon({ status: "reserved", expiresLocalDate: "2026-08-01" }, TODAY)).toBe(true);
  });

  it("만료일 당일은 전이시키지 않는다", () => {
    // 당일은 아직 쓸 수 있고 알림도 "오늘 만료" 를 보내는 날이다.
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: TODAY }, TODAY)).toBe(false);
  });

  it("아직 남은 쿠폰은 건드리지 않는다", () => {
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2026-08-29" }, TODAY)).toBe(false);
  });

  it("이미 사용한 쿠폰을 만료로 덮지 않는다", () => {
    // 사용 완료 기록이 사라지면 5분 내 실행 취소 경로와 충돌한다.
    expect(shouldExpireCoupon({ status: "used", expiresLocalDate: "2026-01-01" }, TODAY)).toBe(false);
  });

  it("이미 만료된 쿠폰을 다시 쓰지 않는다", () => {
    expect(shouldExpireCoupon({ status: "expired", expiresLocalDate: "2026-01-01" }, TODAY)).toBe(false);
  });

  it("휴지통 상태를 만료로 덮지 않는다", () => {
    // 복구 시 상태가 어긋난다.
    expect(shouldExpireCoupon({ status: "deleted", expiresLocalDate: "2026-01-01" }, TODAY)).toBe(false);
    expect(shouldExpireCoupon({ status: "purging", expiresLocalDate: "2026-01-01" }, TODAY)).toBe(false);
  });

  it("status가 없으면 active로 보고 판정한다", () => {
    expect(shouldExpireCoupon({ expiresLocalDate: "2026-08-27" }, TODAY)).toBe(true);
  });

  it("만료일이 없거나 형식이 어긋나면 건드리지 않는다", () => {
    expect(shouldExpireCoupon({ status: "active" }, TODAY)).toBe(false);
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2026/08/27" }, TODAY)).toBe(false);
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "" }, TODAY)).toBe(false);
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: 20260827 }, TODAY)).toBe(false);
  });

  it("연·월 경계를 문자열 비교로 정확히 다룬다", () => {
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2025-12-31" }, "2026-01-01")).toBe(true);
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2026-01-01" }, "2026-01-01")).toBe(false);
    expect(shouldExpireCoupon({ status: "active", expiresLocalDate: "2026-02-28" }, "2026-03-01")).toBe(true);
  });
});

describe("selectCouponsToExpire", () => {
  it("전이 대상만 남긴다", () => {
    const candidates = [
      { id: "a", status: "active", expiresLocalDate: "2026-08-01" },
      { id: "b", status: "used", expiresLocalDate: "2026-08-01" },
      { id: "c", status: "active", expiresLocalDate: "2026-12-31" },
      { id: "d", status: "reserved", expiresLocalDate: "2026-08-27" }
    ];
    expect(selectCouponsToExpire(candidates, TODAY).map((c) => c.id)).toEqual(["a", "d"]);
  });

  it("대상이 없으면 빈 배열", () => {
    expect(selectCouponsToExpire([{ status: "used", expiresLocalDate: "2026-01-01" }], TODAY)).toEqual([]);
  });
});
