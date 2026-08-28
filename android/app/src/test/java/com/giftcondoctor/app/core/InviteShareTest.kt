package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteShareTest {
    @Test
    fun shareMessageExplainsWhatTheCodeIsFor() {
        // 코드만 받은 사람은 이게 무엇이고 어디에 넣어야 하는지 알 수 없다.
        val message = inviteShareMessage("가족 쿠폰방", "ABC123")
        assertTrue(message.contains("ABC123"))
        assertTrue(message.contains("가족 쿠폰방"))
        assertTrue(message.contains("기프티콘닥터"))
        assertTrue(message.contains("초대코드로 입장"))
    }

    @Test
    fun blankRoomNameFallsBackToAGenericLabel() {
        val message = inviteShareMessage("   ", "ABC123")
        assertTrue(message.contains("쿠폰방"))
        assertTrue(message.contains("ABC123"))
    }

    @Test
    fun displayFormattingGroupsDigitsForReadability() {
        assertEquals("ABC 123", formatInviteCodeForDisplay("ABC123"))
        assertEquals("AB1 2C3 4D", formatInviteCodeForDisplay("AB12C34D"))
    }

    @Test
    fun shortCodesAreLeftAlone() {
        // 4자 이하는 끊을 이유가 없다.
        assertEquals("AB12", formatInviteCodeForDisplay("AB12"))
        assertEquals("A", formatInviteCodeForDisplay("A"))
    }

    @Test
    fun displayFormattingNeverLeaksIntoTheSharedCode() {
        // 표시용 공백이 실제 코드에 섞이면 입장이 실패한다.
        val code = "ABC123"
        assertTrue(formatInviteCodeForDisplay(code).contains(" "))
        assertTrue(inviteShareMessage("방", code).contains(code))
        assertEquals(false, inviteShareMessage("방", code).contains("ABC 123"))
    }

    @Test
    fun expiredInviteCodeIsNotUsable() {
        // 서버는 입장 시 만료를 검사해 거부한다. 앱이 쓸 수 없는 코드를 공유하게
        // 두면 상대는 입장에 실패하고 보낸 사람도 이유를 모른다.
        val now = 1_700_000_000_000L
        assertEquals(false, isInviteCodeUsable(now - 1, now))
        assertEquals(false, isInviteCodeUsable(now, now))
    }

    @Test
    fun futureInviteCodeIsUsable() {
        val now = 1_700_000_000_000L
        assertTrue(isInviteCodeUsable(now + 1, now))
    }

    @Test
    fun codeWithoutExpiryIsTreatedAsUsable() {
        // 푸시 테스트방처럼 만료가 없는 코드가 있다.
        assertTrue(isInviteCodeUsable(null, 1_700_000_000_000L))
    }
}
