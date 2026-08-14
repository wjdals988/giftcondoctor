package com.giftcondoctor.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.giftcondoctor.app.data.CouponImageLoader
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.screens.RoomDashboard
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale

class RoomDashboardInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        CouponImageLoader.clear()
        CouponImageLoader.resetMetrics()
    }

    @Test
    fun rendersAndScrollsOneHundredCouponsWithoutImageRequests() {
        val coupons = testCoupons(100, hasImages = false)

        composeRule.setContent {
            GDTheme {
                RoomDashboard(
                    roomId = "benchmark-room",
                    coupons = coupons,
                    isOwner = true,
                    hasMore = false,
                    isLoadingMore = false,
                    pagingError = null,
                    onLoadMore = {},
                    onRetry = {},
                    onOpenCoupon = {},
                    onAddCoupon = {},
                    modifier = Modifier
                )
            }
        }

        composeRule.onNodeWithText("불러온 100개 중 100개 · 만료 임박순").assertExists()
        composeRule.onNodeWithTag("coupon-list").performScrollToNode(hasText("쿠폰 100"))
        composeRule.onNodeWithText("쿠폰 100").assertExists()
    }

    @Test
    fun renders24ThumbnailsThenReusesMemoryCacheWhileScrollingBack() {
        val coupons = testCoupons(24, hasImages = true)
        val payload = realisticJpegPayload()
        CouponImageLoader.clear()
        CouponImageLoader.resetMetrics()
        val pssBeforeKb = Debug.getPss()

        composeRule.setContent {
            GDTheme {
                RoomDashboard(
                    roomId = "benchmark-room",
                    coupons = coupons,
                    isOwner = true,
                    hasMore = false,
                    isLoadingMore = false,
                    pagingError = null,
                    onLoadMore = {},
                    onRetry = {},
                    onOpenCoupon = {},
                    onAddCoupon = {},
                    modifier = Modifier,
                    thumbnailLoader = { _, coupon, width, height ->
                        CouponImageLoader.loadForInstrumentation(
                            cacheKey = "dashboard-${coupon.id}",
                            targetWidth = width,
                            targetHeight = height
                        ) { payload.copyOf() }
                    }
                )
            }
        }

        val missStartedAt = SystemClock.elapsedRealtimeNanos()
        coupons.forEach { coupon ->
            scrollToThumbnail(coupon)
        }
        val missMillis = elapsedMillis(missStartedAt)
        val afterMiss = CouponImageLoader.metricsSnapshot()
        val pssAfterMissKb = Debug.getPss()

        val hitStartedAt = SystemClock.elapsedRealtimeNanos()
        coupons.asReversed().forEach { coupon ->
            scrollToThumbnail(coupon)
        }
        val hitMillis = elapsedMillis(hitStartedAt)
        val afterHit = CouponImageLoader.metricsSnapshot()

        assertEquals(24L, afterMiss.fetchOperations)
        assertEquals(24L, afterMiss.decodedBitmaps)
        assertEquals(24, afterMiss.cacheEntries)
        assertEquals(afterMiss.fetchOperations, afterHit.fetchOperations)
        assertEquals(afterMiss.decodedBitmaps, afterHit.decodedBitmaps)
        assertTrue("역방향 스크롤에서 최소 20개는 memory hit여야 합니다.", afterHit.cacheHits >= 20)
        assertTrue("cache hit 스크롤은 첫 decode 스크롤보다 빨라야 합니다.", hitMillis < missMillis)

        Log.i(
            UI_PERF_TAG,
            String.format(
                Locale.US,
                "count=24 payloadBytes=%d missScrollMs=%.3f hitScrollMs=%.3f cacheHits=%d " +
                    "downloadedBytes=%d cacheBytes=%d pssDeltaKb=%d",
                payload.size,
                missMillis,
                hitMillis,
                afterHit.cacheHits,
                afterHit.downloadedBytes,
                afterHit.cacheBytes,
                pssAfterMissKb - pssBeforeKb
            )
        )
    }

    private fun scrollToThumbnail(coupon: Coupon) {
        composeRule.onNodeWithTag("coupon-list").performScrollToNode(hasText(coupon.title))
        val description = "${coupon.title} 썸네일"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun testCoupons(count: Int, hasImages: Boolean): List<Coupon> = (1..count).map { index ->
        Coupon(
            id = "coupon-$index",
            roomId = "benchmark-room",
            title = "쿠폰 ${index.toString().padStart(3, '0')}",
            brand = if (index % 2 == 0) "카페" else "편의점",
            ownerUid = "owner",
            imageBlobPath = if (hasImages) "rooms/benchmark-room/coupons/coupon-$index/original.jpg" else "",
            thumbnailBlobPath = if (hasImages) "rooms/benchmark-room/coupons/coupon-$index/thumbnail.webp" else null,
            imageWidth = null,
            imageHeight = null,
            expiresLocalDate = LocalDate.now().plusDays(index.toLong()),
            timezone = "Asia/Seoul",
            status = "active",
            reservedByUid = null,
            usedByUid = null,
            visibility = "room",
            notifyTarget = "allMembers"
        )
    }

    private fun realisticJpegPayload(): ByteArray {
        val width = 1280
        val height = 720
        val pixels = IntArray(width * height) { offset ->
            val x = offset % width
            val y = offset / width
            val noise = ((x / 13) * 37 + (y / 11) * 19) and 0xff
            Color.rgb(
                (x * 255 / width + noise / 3) and 0xff,
                (y * 255 / height + noise / 2) and 0xff,
                ((x + y) / 8 + noise) and 0xff
            )
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0

    private companion object {
        const val UI_PERF_TAG = "CouponImageUiPerf"
    }
}
