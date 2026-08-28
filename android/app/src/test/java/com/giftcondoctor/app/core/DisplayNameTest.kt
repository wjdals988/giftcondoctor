package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UID_A = "abc123XYZ789def456ghi012jk7A"
private const val UID_B = "zzz999AAA111bbb222ccc333dd8B"

class DisplayNameTest {
    @Test
    fun nameWinsWhenPresent() {
        assertEquals("홍길동", resolveDisplayName("홍길동", "a@b.com", UID_A))
    }

    @Test
    fun emailIsUsedWhenNameIsMissing() {
        assertEquals("a@b.com", resolveDisplayName(null, "a@b.com", UID_A))
    }

    @Test
    fun blankNameIsTreatedAsMissing() {
        // 그대로 쓰면 화면에서 빈 줄이 된다.
        assertEquals("a@b.com", resolveDisplayName("   ", "a@b.com", UID_A))
        assertEquals(fallbackDisplayName(UID_A), resolveDisplayName("\t", null, UID_A))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("홍길동", resolveDisplayName("  홍길동  ", null, UID_A))
    }

    @Test
    fun uidFallbackIsUsedWhenNameAndEmailAreMissing() {
        assertEquals("사용자 JK7A", resolveDisplayName(null, null, UID_A))
    }

    @Test
    fun differentAccountsGetDifferentNames() {
        // 이 변경의 핵심이다. 같은 방에서 구분되지 않으면 방장이 누구를 제거하는지
        // 모르는 채 되돌릴 수 없는 동작을 하게 된다.
        assertNotEquals(
            resolveDisplayName(null, null, UID_A),
            resolveDisplayName(null, null, UID_B)
        )
    }

    @Test
    fun fallbackDoesNotLeakTheWholeUid() {
        // 화면에서 28자는 너무 길고, 다른 사용자에게 계정 식별자를 그대로 보여줄
        // 이유도 없다.
        val label = fallbackDisplayName(UID_A)
        assertEquals(false, label.contains(UID_A))
        assertTrue(label.length < 12)
    }

    @Test
    fun fallbackHandlesShortAndEmptyUid() {
        assertEquals("사용자 AB", fallbackDisplayName("ab"))
        assertEquals("이름 없는 사용자", fallbackDisplayName(""))
        assertEquals("이름 없는 사용자", fallbackDisplayName("   "))
    }
}
