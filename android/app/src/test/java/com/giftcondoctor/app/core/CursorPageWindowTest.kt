package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorPageWindowTest {
    @Test
    fun `첫 페이지는 sentinel을 제외하고 다음 페이지 여부를 반환한다`() {
        val window = selectCursorPageWindow(listOf("a", "b", "c"), 2, null) { it }

        assertEquals(listOf("a", "b"), window.items)
        assertEquals("b", window.cursor)
        assertTrue(window.hasMore)
    }

    @Test
    fun `다음 페이지는 anchor를 겹쳐 보존하고 중복 제거를 위임한다`() {
        val window = selectCursorPageWindow(listOf("b", "c", "d", "e"), 2, "b") { it }

        assertEquals(listOf("b", "c", "d"), window.items)
        assertEquals("d", window.cursor)
        assertTrue(window.hasMore)
    }

    @Test
    fun `anchor가 삭제됐으면 새 문서 pageSize개를 사용한다`() {
        val window = selectCursorPageWindow(listOf("c", "d", "e"), 2, "b") { it }

        assertEquals(listOf("c", "d"), window.items)
        assertEquals("d", window.cursor)
        assertTrue(window.hasMore)
    }

    @Test
    fun `후속 문서가 없으면 마지막 페이지로 판정한다`() {
        val window = selectCursorPageWindow(listOf("b", "c"), 2, "b") { it }

        assertEquals(listOf("b", "c"), window.items)
        assertFalse(window.hasMore)
    }

    @Test
    fun `목록 끝 네 항목 안에 진입하면 다음 페이지를 선조회한다`() {
        assertTrue(shouldLoadNextPage(16, 20, hasMore = true, isLoading = false))
        assertFalse(shouldLoadNextPage(15, 20, hasMore = true, isLoading = false))
    }

    @Test
    fun `로딩 중이거나 다음 페이지가 없으면 중복 요청하지 않는다`() {
        assertFalse(shouldLoadNextPage(19, 20, hasMore = true, isLoading = true))
        assertFalse(shouldLoadNextPage(19, 20, hasMore = false, isLoading = false))
    }

    @Test
    fun `100개 문서를 12개 cursor로 읽어도 누락과 중복이 없다`() {
        val allItems = (1..100).map { it.toString().padStart(3, '0') }
        val loadedItems = linkedSetOf<String>()
        var anchorId: String? = null
        var pageCount = 0
        var hasMore: Boolean

        do {
            val startIndex = anchorId?.let(allItems::indexOf) ?: 0
            val queryLimit = 12 + if (anchorId == null) 1 else 2
            val window = selectCursorPageWindow(
                candidates = allItems.drop(startIndex).take(queryLimit),
                pageSize = 12,
                anchorId = anchorId
            ) { it }
            loadedItems += window.items
            anchorId = window.cursor
            hasMore = window.hasMore
            pageCount += 1
        } while (hasMore)

        assertEquals(allItems, loadedItems.toList())
        assertEquals(9, pageCount)
    }
}
