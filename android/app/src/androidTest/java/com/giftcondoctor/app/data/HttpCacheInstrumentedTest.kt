package com.giftcondoctor.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * HTTP 디스크 캐시가 실제로 준비되는지 검증한다.
 *
 * 서버는 썸네일에 `Cache-Control: private, max-age=3600` 을 보내지만 OkHttp 는
 * Cache 가 설정돼야 그 헤더를 쓴다. 캐시가 조용히 null 이면 헤더가 버려지고 앱은
 * 매번 네트워크로 다시 받는다. 그 상태는 화면에서 "조금 느리다" 로만 보이므로
 * 회귀를 눈으로 잡을 수 없다. 그래서 테스트로 고정한다.
 */
@RunWith(AndroidJUnit4::class)
class HttpCacheInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installProvidesACacheThatOkHttpCanUse() {
        HttpCache.install(context)
        val cache = HttpCache.current()
        assertNotNull("Cache 가 null 이면 서버의 max-age 헤더가 버려진다", cache)

        // 클라이언트가 실제로 이 캐시를 물고 있는지 확인한다.
        val client = OkHttpClient.Builder().cache(cache).build()
        assertNotNull(client.cache)
        assertEquals(cache, client.cache)
    }

    @Test
    fun cacheLivesUnderTheAppCacheDirectory() {
        HttpCache.install(context)
        val directory = HttpCache.current()?.directory
        assertNotNull(directory)
        // cacheDir 아래여야 저장 공간이 부족할 때 시스템이 회수한다.
        assertTrue(
            "캐시는 cacheDir 하위여야 한다: $directory",
            directory!!.absolutePath.startsWith(File(context.cacheDir, "").absolutePath)
        )
    }

    @Test
    fun installIsIdempotent() {
        HttpCache.install(context)
        val first = HttpCache.current()
        HttpCache.install(context)
        assertEquals("재호출이 캐시를 갈아엎으면 진행 중 요청이 깨진다", first, HttpCache.current())
    }

    @Test
    fun evictAllIsSafeToCallAndClearsEntries() {
        HttpCache.install(context)
        // 로그아웃 경로에서 호출된다. 실패해도 로그아웃을 막지 않아야 한다.
        HttpCache.evictAll()
        assertEquals(0L, HttpCache.current()?.size())
    }
}
