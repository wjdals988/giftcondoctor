package com.giftcondoctor.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.giftcondoctor.app"

internal fun MacrobenchmarkScope.startCouponList() {
    val intent = Intent().apply {
        component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.BenchmarkActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    startActivityAndWait(intent)
}

internal fun MacrobenchmarkScope.scrollCouponList() {
    val couponList = device.wait(Until.findObject(By.res("coupon-list")), 5_000)
        ?: error("100개 쿠폰 목록을 찾지 못했습니다.")
    couponList.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        couponList.fling(Direction.DOWN)
        device.waitForIdle(1_000)
    }
}
