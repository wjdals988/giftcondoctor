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

internal fun MacrobenchmarkScope.startCouponImage() {
    val intent = Intent().apply {
        component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.BenchmarkImageActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    startActivityAndWait(intent)
    device.wait(Until.findObject(By.res("zoomed-coupon-image")), 5_000)
        ?: error("확대 가능한 쿠폰 이미지를 찾지 못했습니다.")
}

internal fun MacrobenchmarkScope.pinchCouponImage() {
    val couponImage = device.wait(Until.findObject(By.res("zoomed-coupon-image")), 5_000)
        ?: error("확대 가능한 쿠폰 이미지를 찾지 못했습니다.")
    couponImage.setGestureMargin(device.displayWidth / 5)
    repeat(3) {
        couponImage.pinchOpen(0.5f, 1_000)
        couponImage.pinchClose(0.5f, 1_000)
    }
    device.waitForIdle(1_000)
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
