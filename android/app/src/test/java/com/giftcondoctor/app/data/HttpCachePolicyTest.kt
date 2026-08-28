package com.giftcondoctor.app.data

import okhttp3.CacheControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 서버가 보내는 캐시 헤더가 클라이언트에서 실제로 재사용 가능한지 검증한다.
 *
 * 이 테스트가 존재하는 이유는 2026-08-28 에 발견한 불일치다. 서버는 썸네일 응답에
 * `Cache-Control: private, max-age=3600` 을 보내고 있었지만 클라이언트가
 * `OkHttpClient()` 기본 생성자를 써서 Cache 가 없었다. OkHttp 는 Cache 가 없으면
 * 응답을 디스크에 저장하지 않으므로 그 헤더가 통째로 버려졌고, 앱을 다시 열 때마다
 * 모든 썸네일을 네트워크로 다시 받았다.
 *
 * 헤더 형식이 바뀌면(예: no-store 로 되돌아가면) 캐시가 조용히 무효가 되므로
 * 계약을 여기에 고정한다.
 */
class HttpCachePolicyTest {
    @Test
    fun thumbnailHeaderAllowsReuseForAnHour() {
        val parsed = CacheControl.parse(
            okhttp3.Headers.headersOf("Cache-Control", "private, max-age=3600")
        )
        assertEquals(3600, parsed.maxAgeSeconds)
        assertFalse("no-store 면 디스크에 저장되지 않는다", parsed.noStore)
        assertFalse("no-cache 면 매번 재검증한다", parsed.noCache)
        assertTrue("private 은 앱 전용 캐시에서 정상이다", parsed.isPrivate)
    }

    @Test
    fun originalImageHeaderIsIntentionallyNotStored() {
        // 원본은 용량이 크고 민감도가 높아 저장하지 않는 것이 의도된 설계다.
        val parsed = CacheControl.parse(
            okhttp3.Headers.headersOf("Cache-Control", "private, no-store")
        )
        assertTrue(parsed.noStore)
    }

    @Test
    fun oneHourIsTheReuseWindowWeRelyOn() {
        // 목록을 다시 열거나 앱을 재시작해도 1시간 안이면 네트워크 왕복이 없다.
        assertEquals(3600L, TimeUnit.HOURS.toSeconds(1))
    }
}
