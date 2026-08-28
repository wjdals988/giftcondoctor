package com.giftcondoctor.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.giftcondoctor.app.MainActivity
import com.giftcondoctor.app.R
import com.giftcondoctor.app.core.MAX_RECENT_COUPON_SHORTCUTS
import com.giftcondoctor.app.core.RecentCoupon
import com.giftcondoctor.app.core.recentCouponShortcutLabel
import com.giftcondoctor.app.core.withRecentCoupon
import com.giftcondoctor.app.core.withoutRecentCoupon
import org.json.JSONArray
import org.json.JSONObject

/**
 * 최근 연 쿠폰을 앱 아이콘 길게 누르기 바로가기로 노출한다.
 *
 * 매장에서 바코드까지 네 단계(앱 → 방 목록 → 방 → 쿠폰 → 바코드)를 거쳐야 했다.
 * 딥링크가 이미 쿠폰 단위까지 뚫려 있으므로 바로가기만 붙이면 그 경로가 크게 줄어든다.
 *
 * 목록은 SharedPreferences 에 둔다. 서버에 둘 이유가 없다. 기기별 사용 습관이고,
 * 다른 기기와 동기화되면 오히려 낯선 바로가기가 생긴다.
 */
object RecentCouponShortcuts {
    private const val PREFS = "recent_coupon_shortcuts"
    private const val KEY = "recent"

    /** 쿠폰을 열 때 호출한다. 실패해도 화면 동작을 막지 않는다. */
    fun record(context: Context, roomId: String, couponId: String, title: String) {
        runCatching {
            val updated = withRecentCoupon(
                read(context),
                RecentCoupon(roomId = roomId, couponId = couponId, title = title)
            )
            write(context, updated)
            publish(context, updated)
        }
    }

    /**
     * 삭제되거나 접근할 수 없게 된 쿠폰을 목록과 바로가기에서 뺀다.
     *
     * 바로가기를 눌렀는데 "쿠폰을 찾을 수 없습니다" 가 뜨면 앱이 고장난 것처럼 보인다.
     */
    fun forget(context: Context, roomId: String, couponId: String) {
        runCatching {
            val updated = withoutRecentCoupon(read(context), roomId, couponId)
            write(context, updated)
            publish(context, updated)
        }
    }

    /** 로그아웃 시 호출한다. 다음 사용자에게 이전 계정의 쿠폰 이름이 보이면 안 된다. */
    fun clear(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
    }

    private fun publish(context: Context, coupons: List<RecentCoupon>) {
        if (!ShortcutManagerCompat.isRateLimitingActive(context)) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        }
        coupons.take(MAX_RECENT_COUPON_SHORTCUTS).forEachIndexed { index, coupon ->
            val uri = Uri.parse("giftcondoctor://rooms/${coupon.roomId}/coupons/${coupon.couponId}")
            val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            val shortcut = ShortcutInfoCompat.Builder(context, "coupon-${coupon.roomId}-${coupon.couponId}")
                .setShortLabel(recentCouponShortcutLabel(coupon.title))
                .setLongLabel(recentCouponShortcutLabel(coupon.title))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(intent)
                .setRank(index)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    private fun read(context: Context): List<RecentCoupon> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val roomId = item.optString("roomId")
                val couponId = item.optString("couponId")
                if (roomId.isBlank() || couponId.isBlank()) continue
                add(RecentCoupon(roomId, couponId, item.optString("title")))
            }
        }
    }.getOrElse { emptyList() }

    private fun write(context: Context, coupons: List<RecentCoupon>) {
        val array = JSONArray()
        coupons.forEach { coupon ->
            array.put(
                JSONObject()
                    .put("roomId", coupon.roomId)
                    .put("couponId", coupon.couponId)
                    .put("title", coupon.title)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}
