package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
    @Test
    fun `목록 썸네일은 고해상도 이미지를 강하게 축소한다`() {
        assertEquals(16, bitmapSampleSize(4_000, 3_000, 168, 168))
    }

    @Test
    fun `상세 화면은 표시 크기에 맞는 해상도를 유지한다`() {
        assertEquals(2, bitmapSampleSize(4_000, 3_000, 1_080, 1_440))
    }

    @Test
    fun `작은 이미지와 잘못된 크기는 원본 배율을 사용한다`() {
        assertEquals(1, bitmapSampleSize(320, 240, 1_080, 1_440))
        assertEquals(1, bitmapSampleSize(0, 0, 168, 168))
    }
}
