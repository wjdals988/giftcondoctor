package com.giftcondoctor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.screens.RoomDashboard
import com.giftcondoctor.app.ui.theme.GDTheme
import java.time.LocalDate

class BenchmarkActivity : ComponentActivity() {
    private val coupons = benchmarkCoupons()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
    }
}

private fun benchmarkCoupons(): List<Coupon> = (1..100).map { index ->
    Coupon(
        id = "coupon-$index",
        roomId = "benchmark-room",
        title = "쿠폰 ${index.toString().padStart(3, '0')}",
        brand = if (index % 2 == 0) "카페" else "편의점",
        ownerUid = "owner",
        imageBlobPath = "",
        thumbnailBlobPath = null,
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
