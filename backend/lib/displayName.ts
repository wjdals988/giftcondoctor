/**
 * 사용자에게 보이는 표시명 생성.
 *
 * 기존에는 세 곳에서 `displayName ?? email ?? "이름 없음"` 을 각자 구현했다.
 * 마지막 폴백이 고정 문자열이라, 이름과 이메일이 모두 없는 계정이 한 방에 둘
 * 이상이면 전부 "이름 없음" 으로 표시되어 구분이 불가능했다.
 *
 * 구분 불가는 표시 문제로 끝나지 않는다. 방장이 멤버를 제거할 때 확인
 * 다이얼로그가 표시명을 그대로 보여주므로, 같은 이름이 여럿이면 누구를 제거하는지
 * 모르는 채 되돌릴 수 없는 동작을 하게 된다.
 *
 * Google 계정은 이름 없이 만들 수 있고 이메일 범위를 주지 않는 경우도 있다.
 * 그래서 uid 기반 접미사를 마지막 폴백으로 둔다. uid 는 계정마다 고유하므로
 * 같은 방에서 두 사람이 같은 표시명을 갖지 않는다.
 */

/** 이름·이메일이 모두 없을 때 uid 에서 뽑는 식별 접미사 길이. */
const UID_SUFFIX_LENGTH = 4;

/**
 * uid 로부터 사람이 읽을 수 있는 대체 표시명을 만든다.
 *
 * uid 전체를 노출하지 않는 이유는 두 가지다. 화면에서 28자는 너무 길고, 다른
 * 사용자에게 계정 식별자를 그대로 보여줄 이유도 없다. 뒤 4자리면 같은 방 안에서
 * 구분하기에 충분하다.
 */
export function fallbackDisplayName(uid: string): string {
  const trimmed = uid.trim();
  if (trimmed === "") return "이름 없는 사용자";
  const suffix = trimmed.slice(-UID_SUFFIX_LENGTH).toUpperCase();
  return `사용자 ${suffix}`;
}

/**
 * 표시명을 결정한다. 앞의 값이 비어 있지 않으면 그것을 쓴다.
 *
 * 공백만 있는 이름은 없는 것으로 본다. OCR 이나 외부 계정에서 넘어온 값이
 * 공백뿐인 경우가 있고, 그대로 쓰면 화면에서 빈 줄이 된다.
 */
export function resolveDisplayName(
  name: string | null | undefined,
  email: string | null | undefined,
  uid: string
): string {
  if (typeof name === "string" && name.trim() !== "") return name.trim();
  if (typeof email === "string" && email.trim() !== "") return email.trim();
  return fallbackDisplayName(uid);
}
