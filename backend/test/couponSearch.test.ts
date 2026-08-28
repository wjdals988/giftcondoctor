import { describe, expect, it } from "vitest";
import {
  SEARCH_MIN_QUERY_LENGTH,
  matchesCouponQuery,
  normalizeSearchQuery,
  selectSearchResults,
  type RawSearchCoupon
} from "../lib/couponSearch";

function coupon(overrides: Partial<RawSearchCoupon> = {}): RawSearchCoupon {
  return {
    roomId: "room-1",
    roomName: "우리집",
    couponId: "coupon-1",
    title: "스타벅스 아메리카노",
    brand: "스타벅스",
    status: "active",
    visibility: "room",
    ownerUid: "me",
    expiresLocalDate: "2026-12-31",
    ...overrides
  };
}

describe("normalizeSearchQuery", () => {
  it("한 글자 질의는 받지 않는다", () => {
    // 한 글자로는 거의 모든 쿠폰이 걸려 읽기만 낭비된다.
    expect(normalizeSearchQuery("커")).toBeNull();
    expect(SEARCH_MIN_QUERY_LENGTH).toBe(2);
  });

  it("빈 값과 공백만 있는 값은 null 이다", () => {
    expect(normalizeSearchQuery(null)).toBeNull();
    expect(normalizeSearchQuery("   ")).toBeNull();
  });

  it("공백을 접고 소문자로 낮춘다", () => {
    expect(normalizeSearchQuery("  Star   Bucks ")).toBe("star bucks");
  });
});

describe("matchesCouponQuery", () => {
  it("제목과 브랜드 어느 쪽이든 걸린다", () => {
    expect(matchesCouponQuery(coupon(), "아메리카노")).toBe(true);
    expect(matchesCouponQuery(coupon(), "스타벅스")).toBe(true);
    expect(matchesCouponQuery(coupon(), "치킨")).toBe(false);
  });

  it("영문 브랜드는 대소문자를 가리지 않는다", () => {
    expect(matchesCouponQuery(coupon({ brand: "Starbucks" }), "starbucks")).toBe(true);
  });
});

describe("selectSearchResults", () => {
  it("남의 비공개 쿠폰은 제외한다", () => {
    const raw = [coupon({ visibility: "private", ownerUid: "someone-else" })];
    expect(selectSearchResults(raw, "me", "스타벅스").coupons).toHaveLength(0);
  });

  it("내 비공개 쿠폰은 포함한다", () => {
    const raw = [coupon({ visibility: "private", ownerUid: "me" })];
    expect(selectSearchResults(raw, "me", "스타벅스").coupons).toHaveLength(1);
  });

  it("휴지통 상태는 제외한다", () => {
    const raw = [coupon({ status: "deleted" }), coupon({ couponId: "c2", status: "purging" })];
    expect(selectSearchResults(raw, "me", "스타벅스").coupons).toHaveLength(0);
  });

  it("사용·만료 쿠폰은 남기되 뒤로 민다", () => {
    // "그 쿠폰 썼던가?" 를 확인하려는 질의가 실제로 많다. 결과가 비면 등록
    // 자체를 안 했다고 오해한다.
    const raw = [
      coupon({ couponId: "used", status: "used", expiresLocalDate: "2026-01-01" }),
      coupon({ couponId: "active", status: "active", expiresLocalDate: "2026-12-31" })
    ];
    const { coupons } = selectSearchResults(raw, "me", "스타벅스");
    expect(coupons.map((c) => c.couponId)).toEqual(["active", "used"]);
  });

  it("쓸 수 있는 쿠폰 안에서는 만료가 급한 순이다", () => {
    const raw = [
      coupon({ couponId: "late", expiresLocalDate: "2026-12-31" }),
      coupon({ couponId: "soon", expiresLocalDate: "2026-09-01" })
    ];
    const { coupons } = selectSearchResults(raw, "me", "스타벅스");
    expect(coupons.map((c) => c.couponId)).toEqual(["soon", "late"]);
  });

  it("제목이 비어 있으면 대체 문구를 쓴다", () => {
    const raw = [coupon({ title: "  " })];
    expect(selectSearchResults(raw, "me", "스타벅스").coupons[0].title).toBe("이름 없는 쿠폰");
  });

  it("상한을 넘으면 잘렸다고 알린다", () => {
    // 잘린 사실을 숨기면 "없다" 는 오해를 만든다.
    const raw = Array.from({ length: 5 }, (_, index) =>
      coupon({ couponId: `c${index}`, expiresLocalDate: `2026-09-0${index + 1}` })
    );
    const result = selectSearchResults(raw, "me", "스타벅스", 3);
    expect(result.coupons).toHaveLength(3);
    expect(result.truncated).toBe(true);
  });
});
