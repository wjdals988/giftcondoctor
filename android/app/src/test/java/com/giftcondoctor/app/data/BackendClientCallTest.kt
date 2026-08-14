package com.giftcondoctor.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackendClientCallTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun buffersResponseOffTheCallingCoroutine() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/image")).build())

            val response = async { call.awaitBufferedResponse() }.await()

            assertEquals(200, response.code)
            assertEquals("ok", response.bodyText())
        }
    }

    @Test
    fun cancellationStopsAnInFlightBodyDownload() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE)
            )
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/image")).build())
            val download = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitBufferedResponse() }
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)

            download.cancelAndJoin()

            assertTrue(call.isCanceled())
        }
    }

    @Test
    fun streamsImageResponseToFile() = runTest {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(payload)))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/image")).build())
            val destination = File(temporaryFolder.root, "downloaded.image")

            val response = call.awaitFileResponse(destination, maxBytes = payload.size.toLong())

            assertTrue(response.isSuccessful)
            assertEquals(payload.size.toLong(), response.byteCount)
            assertArrayEquals(payload, destination.readBytes())
        }
    }

    @Test
    fun rejectsOversizedImageAndDeletesPartialFile() = runTest {
        val payload = ByteArray(2 * 1024) { 1 }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(payload), 256))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/image")).build())
            val destination = File(temporaryFolder.root, "oversized.image")

            val result = runCatching { call.awaitFileResponse(destination, maxBytes = 1024) }

            assertTrue(result.exceptionOrNull() is IOException)
            assertFalse(destination.exists())
        }
    }

    @Test
    fun cancellationDeletesDestinationFile() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/image")).build())
            val destination = File(temporaryFolder.root, "cancelled.image")
            val download = launch(start = CoroutineStart.UNDISPATCHED) {
                call.awaitFileResponse(destination, maxBytes = 1024)
            }
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)

            download.cancelAndJoin()

            assertTrue(call.isCanceled())
            assertFalse(destination.exists())
        }
    }
}
