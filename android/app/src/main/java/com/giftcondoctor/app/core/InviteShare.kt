package com.giftcondoctor.app.core

/**
 * 초대 코드를 다른 사람에게 전달할 때 쓰는 문구.
 *
 * 이 앱의 공유 기능은 초대 코드를 상대에게 전달해야 성립하는데, 지금까지 화면에는
 * `초대코드: ABC123` 이라는 평문만 있었다. 복사 버튼도 공유 동작도 없어서 사용자가
 * 코드를 눈으로 읽고 카카오톡에 옮겨 적어야 했다.
 *
 * 공유 문구에는 코드만 넣지 않는다. 받는 사람이 코드만 보면 이게 무엇이고 어디에
 * 넣어야 하는지 알 수 없다. 앱 이름과 사용처를 함께 적는다.
 */
fun inviteShareMessage(roomName: String, inviteCode: String): String {
    val name = roomName.trim().ifEmpty { "쿠폰방" }
    return "기프티콘닥터에서 \"$name\" 쿠폰방에 초대합니다.\n" +
        "초대코드: $inviteCode\n" +
        "앱에서 방 입장 > 초대코드로 입장에 코드를 넣어 주세요."
}

/**
 * 초대 코드를 화면에서 읽기 쉽게 끊어 준다.
 *
 * 사람이 코드를 소리 내어 읽거나 옮겨 적을 때 6자리가 붙어 있으면 자리를 놓치기
 * 쉽다. 3자리씩 끊으면 읽는 단위가 생긴다. 복사·공유에는 원본을 그대로 쓴다.
 * 표시용 공백이 실제 코드에 섞이면 입장이 실패한다.
 */
fun formatInviteCodeForDisplay(inviteCode: String): String {
    val trimmed = inviteCode.trim()
    if (trimmed.length <= 4) return trimmed
    return trimmed.chunked(3).joinToString(" ")
}

/**
 * 초대 코드가 아직 쓸 수 있는지 판정한다.
 *
 * 서버는 입장 시 `inviteExpiresAt` 을 검사해 만료된 코드를 거부한다
 * (`backend/app/api/rooms/join/route.ts`). 그런데 앱은 만료 시각을 그냥 한 줄로
 * 보여줄 뿐 만료 여부를 알려주지 않았다. 2026-08-28 실기기 확인에서 3개월 전에
 * 만료된 코드가 정상인 것처럼 표시되고 복사·공유까지 제공되고 있었다.
 *
 * 쓸 수 없는 코드를 상대에게 보내면 상대는 입장에 실패하고 이유를 모른다. 보낸
 * 사람도 왜 안 되는지 알 수 없다.
 *
 * expiresAt 이 null 이면 만료가 없는 코드로 본다. 푸시 테스트방이 그렇다.
 */
fun isInviteCodeUsable(expiresAtEpochMillis: Long?, nowEpochMillis: Long): Boolean {
    if (expiresAtEpochMillis == null) return true
    return expiresAtEpochMillis > nowEpochMillis
}
