package com.giftcondoctor.app.data

import com.giftcondoctor.app.core.selectCursorPageWindow
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.toCoupon
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_COUPON_PAGE_SIZE_PER_VISIBILITY = 12

data class CouponPagingState(
    val coupons: List<Coupon> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
)

class CouponPager internal constructor(
    private val roomId: String,
    private val uid: String?,
    private val firestore: FirebaseFirestore,
    private val pageSize: Int = DEFAULT_COUPON_PAGE_SIZE_PER_VISIBILITY
) {
    private enum class Source { Room, Private }

    private data class PageSlot(
        val anchor: DocumentSnapshot?,
        var documents: Map<String, Coupon> = emptyMap(),
        var cursor: DocumentSnapshot? = null,
        var hasMore: Boolean = false,
        var initialized: Boolean = false,
        var errorMessage: String? = null,
        var registration: ListenerRegistration? = null
    )

    private val lock = Any()
    private val pages = Source.entries.associateWith { mutableListOf<PageSlot>() }
    private val _state = MutableStateFlow(CouponPagingState())
    val state: StateFlow<CouponPagingState> = _state.asStateFlow()

    private var generation = 0
    private var pendingInitialPages = 0
    private var pendingMorePages = 0

    init {
        require(pageSize > 0) { "쿠폰 페이지 크기는 1 이상이어야 합니다." }
        refresh()
    }

    fun refresh() = synchronized(lock) {
        val previouslyLoadedCoupons = _state.value.coupons
        generation += 1
        pages.values.flatten().forEach { it.registration?.remove() }
        pages.values.forEach { it.clear() }
        pendingInitialPages = 0
        pendingMorePages = 0
        _state.value = CouponPagingState(
            coupons = previouslyLoadedCoupons,
            isInitialLoading = true
        )

        attachPage(Source.Room, anchor = null, generation)
        if (uid != null) attachPage(Source.Private, anchor = null, generation)
    }

    fun loadNextPage() = synchronized(lock) {
        if (pendingInitialPages > 0 || pendingMorePages > 0) return

        val currentGeneration = generation
        activeSources().forEach { source ->
            val lastPage = pages.getValue(source).lastOrNull() ?: return@forEach
            val cursor = lastPage.cursor
            if (lastPage.hasMore && cursor != null) {
                attachPage(source, cursor, currentGeneration)
            }
        }
        publishState()
    }

    fun close() = synchronized(lock) {
        generation += 1
        pages.values.flatten().forEach { it.registration?.remove() }
        pages.values.forEach { it.clear() }
    }

    private fun attachPage(source: Source, anchor: DocumentSnapshot?, expectedGeneration: Int) {
        val slot = PageSlot(anchor = anchor)
        pages.getValue(source).add(slot)
        if (anchor == null) pendingInitialPages += 1 else pendingMorePages += 1

        val queryLimit = pageSize + if (anchor == null) 1 else 2
        var query = baseQuery(source).limit(queryLimit.toLong())
        if (anchor != null) query = query.startAt(anchor)
        slot.registration = query.addSnapshotListener { snapshot, error ->
            handleSnapshot(slot, snapshot?.documents.orEmpty(), error, expectedGeneration)
        }
    }

    private fun handleSnapshot(
        slot: PageSlot,
        snapshots: List<DocumentSnapshot>,
        error: FirebaseFirestoreException?,
        expectedGeneration: Int
    ) = synchronized(lock) {
        if (expectedGeneration != generation) return
        markPageInitialized(slot)

        if (error != null) {
            slot.errorMessage = error.localizedMessage ?: "쿠폰 목록을 불러오지 못했습니다."
            slot.hasMore = false
            publishState()
            return
        }

        val window = selectCursorPageWindow(
            candidates = snapshots,
            pageSize = pageSize,
            anchorId = slot.anchor?.id,
            idOf = DocumentSnapshot::getId
        )
        slot.documents = window.items.mapNotNull { it.toCoupon(roomId) }.associateBy(Coupon::id)
        slot.cursor = window.cursor
        slot.hasMore = window.hasMore
        slot.errorMessage = null
        publishState()
    }

    private fun markPageInitialized(slot: PageSlot) {
        if (slot.initialized) return
        slot.initialized = true
        if (slot.anchor == null) pendingInitialPages -= 1 else pendingMorePages -= 1
    }

    private fun baseQuery(source: Source): Query {
        var query: Query = firestore.collection("rooms/$roomId/coupons")
            .whereEqualTo("visibility", if (source == Source.Room) "room" else "private")
        if (source == Source.Private) query = query.whereEqualTo("ownerUid", uid)
        return query
            .orderBy("expiresLocalDate", Query.Direction.ASCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
    }

    private fun activeSources(): List<Source> =
        if (uid == null) listOf(Source.Room) else Source.entries

    private fun publishState() {
        val activePages = activeSources().flatMap { pages.getValue(it) }
        val couponsById = LinkedHashMap<String, Coupon>()
        activePages.forEach { couponsById.putAll(it.documents) }
        val sortedCoupons = couponsById.values.sortedWith(
            compareBy<Coupon> { it.expiresLocalDate }.thenBy { it.title }.thenBy { it.id }
        )
        _state.value = CouponPagingState(
            coupons = sortedCoupons,
            isInitialLoading = pendingInitialPages > 0,
            isLoadingMore = pendingMorePages > 0,
            hasMore = pendingInitialPages == 0 &&
                activeSources().any { pages.getValue(it).lastOrNull()?.hasMore == true },
            errorMessage = activePages.firstNotNullOfOrNull(PageSlot::errorMessage)
        )
    }
}
