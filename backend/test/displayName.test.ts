import { describe, expect, it } from "vitest";
import { fallbackDisplayName, resolveDisplayName } from "../lib/displayName";

const UID_A = "abc123XYZ789def456ghi012jk7A";
const UID_B = "zzz999AAA111bbb222ccc333dd8B";

describe("resolveDisplayName", () => {
  it("이름이 있으면 그대로 쓴다", () => {
    expect(resolveDisplayName("홍길동", "a@b.com", UID_A)).toBe("홍길동");
  });

  it("이름이 없으면 이메일로 대체한다", () => {
    expect(resolveDisplayName(null, "a@b.com", UID_A)).toBe("a@b.com");
    expect(resolveDisplayName(undefined, "a@b.com", UID_A)).toBe("a@b.com");
  });

  it("공백뿐인 이름은 없는 것으로 본다", () => {
    // 그대로 쓰면 화면에서 빈 줄이 된다.
    expect(resolveDisplayName("   ", "a@b.com", UID_A)).toBe("a@b.com");
    expect(resolveDisplayName("\t\n", null, UID_A)).toBe(fallbackDisplayName(UID_A));
  });

  it("앞뒤 공백을 제거한다", () => {
    expect(resolveDisplayName("  홍길동  ", null, UID_A)).toBe("홍길동");
  });

  it("이름과 이메일이 모두 없으면 uid 기반 이름을 만든다", () => {
    expect(resolveDisplayName(null, null, UID_A)).toBe("사용자 JK7A");
  });

  it("서로 다른 계정은 서로 다른 표시명을 갖는다", () => {
    // 이것이 이 변경의 핵심이다. 같은 방에서 구분되지 않으면 방장이 누구를
    // 제거하는지 모르는 채 되돌릴 수 없는 동작을 하게 된다.
    const a = resolveDisplayName(null, null, UID_A);
    const b = resolveDisplayName(null, null, UID_B);
    expect(a).not.toBe(b);
  });
});

describe("fallbackDisplayName", () => {
  it("uid 뒤 4자리를 대문자로 쓴다", () => {
    expect(fallbackDisplayName("aaaaaaaaaaaaaaaaaaaaaaaawxyz")).toBe("사용자 WXYZ");
  });

  it("uid 전체를 노출하지 않는다", () => {
    // 화면에서 28자는 너무 길고, 다른 사용자에게 계정 식별자를 그대로 보여줄 이유도 없다.
    expect(fallbackDisplayName(UID_A)).not.toContain(UID_A);
    expect(fallbackDisplayName(UID_A).length).toBeLessThan(12);
  });

  it("uid가 4자리보다 짧아도 동작한다", () => {
    expect(fallbackDisplayName("ab")).toBe("사용자 AB");
  });

  it("uid가 비어 있으면 고정 문구로 떨어진다", () => {
    expect(fallbackDisplayName("")).toBe("이름 없는 사용자");
    expect(fallbackDisplayName("   ")).toBe("이름 없는 사용자");
  });
});
