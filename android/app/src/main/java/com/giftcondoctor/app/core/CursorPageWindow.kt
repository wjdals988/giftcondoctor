package com.giftcondoctor.app.core

data class CursorPageWindow<T>(
    val items: List<T>,
    val cursor: T?,
    val hasMore: Boolean
)

/**
 * Selects one cursor page while retaining the anchor document as an overlap.
 * The overlap prevents a live previous page from pushing its former last item
 * out of every loaded page when a new document is inserted ahead of it.
 */
fun <T> selectCursorPageWindow(
    candidates: List<T>,
    pageSize: Int,
    anchorId: String?,
    idOf: (T) -> String
): CursorPageWindow<T> {
    require(pageSize > 0) { "페이지 크기는 1 이상이어야 합니다." }

    val includesAnchor = anchorId != null && candidates.firstOrNull()?.let(idOf) == anchorId
    val visibleLimit = pageSize + if (includesAnchor) 1 else 0
    val items = candidates.take(visibleLimit)
    return CursorPageWindow(
        items = items,
        cursor = items.lastOrNull(),
        hasMore = candidates.size > visibleLimit
    )
}

fun shouldLoadNextPage(
    lastVisibleIndex: Int,
    totalItems: Int,
    hasMore: Boolean,
    isLoading: Boolean,
    prefetchDistance: Int = 4
): Boolean {
    require(prefetchDistance > 0) { "선조회 거리는 1 이상이어야 합니다." }
    return hasMore && !isLoading && totalItems > 0 &&
        lastVisibleIndex >= totalItems - prefetchDistance
}
