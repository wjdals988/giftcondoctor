package com.giftcondoctor.app.data

import android.content.Context
import okhttp3.Cache
import java.io.File

/**
 * 이미지 응답을 위한 HTTP 디스크 캐시.
 *
 * 서버는 썸네일 응답에 `Cache-Control: private, max-age=3600` 을 이미 보내고 있다
 * (`backend/app/api/coupons/thumbnail/route.ts:21`,
 *  `backend/app/api/coupons/image/route.ts:39`).
 *
 * 그런데 클라이언트는 `OkHttpClient()` 기본 생성자를 쓰고 있었다. OkHttp 는 Cache 를
 * 명시하지 않으면 응답을 디스크에 저장하지 않으므로, 서버가 "1시간 동안 재사용해도
 * 된다" 고 말해도 그 헤더가 통째로 버려졌다. 앱을 다시 열 때마다, 메모리 LruCache 가
 * 비워질 때마다 모든 썸네일을 네트워크로 다시 받았다.
 *
 * 여기서 Cache 를 붙이면 서버가 이미 보내는 헤더가 즉시 효력을 갖는다. 서버 변경도,
 * 별도 캐시 계층 구현도 필요 없다.
 *
 * ## 크기
 * 썸네일은 장당 수십 KB 수준이고 원본은 `no-store` 라 저장되지 않는다. 50MB 면
 * 수백 장을 담고도 남는다. cacheDir 은 저장 공간이 부족하면 시스템이 회수하므로
 * 사용자 저장 공간을 영구 점유하지 않는다.
 *
 * ## 계정 분리
 * 응답에 `private` 이 붙어 있고 이 캐시는 앱 전용이지만, 캐시 키는 URL 이고 URL 에는
 * 계정 정보가 없다. 한 기기에서 계정을 바꾸면 이전 계정의 이미지가 재사용될 수 있다.
 * 그래서 로그아웃 시 [evictAll] 로 비운다.
 */
object HttpCache {
    private const val DIRECTORY_NAME = "http-cache"
    private const val MAX_SIZE_BYTES = 50L * 1024 * 1024

    @Volatile
    private var cache: Cache? = null

    /** 앱 시작 시 한 번 호출한다. 이미 만들어져 있으면 아무 일도 하지 않는다. */
    fun install(context: Context) {
        if (cache != null) return
        synchronized(this) {
            if (cache != null) return
            cache = runCatching {
                Cache(File(context.applicationContext.cacheDir, DIRECTORY_NAME), MAX_SIZE_BYTES)
            }.getOrNull()
        }
    }

    fun current(): Cache? = cache

    /**
     * 캐시를 비운다.
     *
     * 로그아웃 시 호출한다. 실패해도 로그아웃 자체를 막지 않는다. 캐시 삭제가
     * 안 됐다고 사용자를 로그인 상태에 붙잡아 둘 이유가 없다.
     */
    fun evictAll() {
        runCatching { cache?.evictAll() }
    }
}
