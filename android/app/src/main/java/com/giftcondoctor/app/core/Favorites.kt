package com.giftcondoctor.app.core

/**
 * 즐겨찾기 식별자.
 *
 * 즐겨찾기는 `users/{uid}/favorites/{roomId}__{couponId}` 에 참조만 담는다.
 * 쿠폰 문서에 `favoritedBy` 배열을 두는 대안은 남의 즐겨찾기 때문에 쿠폰 문서가
 * 갱신되고, 그 쓰기를 허용하려면 쿠폰 규칙을 넓혀야 한다. 사용자 하위 컬렉션은
 * 쿠폰 규칙을 건드리지 않는다.
 *
 * 문서 ID 를 참조에서 유도하는 것도 의도다. 임의 ID 를 쓰면 같은 쿠폰이 여러
 * 문서로 중복되고 해제할 때 무엇을 지워야 하는지 알 수 없다. 규칙도 같은 형식을
 * 강제한다(`firestore.rules` `match /favorites/{favoriteId}`).
 */
fun favoriteDocId(roomId: String, couponId: String): String = "${roomId}__$couponId"
