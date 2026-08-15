package com.giftcondoctor.app.ui.viewmodel

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ParallelImageProcessingTest {
    @Test
    fun `analysis callback arrives before slower preparation and total time is parallel`() = runTest {
        val events = mutableListOf<Pair<String, Long>>()

        val result = processImageSelectionInParallel(
            analyze = {
                delay(100)
                "자동 입력"
            },
            prepare = {
                delay(800)
                "업로드 파일"
            },
            onAnalysisComplete = { events += "analysis" to currentTime },
            onPreparationComplete = { events += "preparation" to currentTime }
        )

        assertEquals(listOf("analysis" to 100L, "preparation" to 800L), events)
        assertEquals("자동 입력", result.analysis.getOrThrow())
        assertEquals("업로드 파일", result.preparation.getOrThrow())
        assertEquals(800L, currentTime)
    }

    @Test
    fun `preparation failure does not discard successful analysis`() = runTest {
        val events = mutableListOf<String>()

        val result = processImageSelectionInParallel(
            analyze = {
                delay(200)
                "자동 입력"
            },
            prepare = {
                delay(50)
                throw IOException("준비 실패")
            },
            onAnalysisComplete = { events += "analysis:${it.isSuccess}" },
            onPreparationComplete = { events += "preparation:${it.isSuccess}" }
        )

        assertEquals(listOf("preparation:false", "analysis:true"), events)
        assertEquals("자동 입력", result.analysis.getOrThrow())
        assertTrue(result.preparation.exceptionOrNull() is IOException)
    }

    @Test
    fun `parent cancellation cancels analysis and preparation`() = runTest {
        var analysisCancelled = false
        var preparationCancelled = false
        val job = launch {
            processImageSelectionInParallel(
                analyze = {
                    try {
                        awaitCancellation()
                    } finally {
                        analysisCancelled = true
                    }
                },
                prepare = {
                    try {
                        awaitCancellation()
                    } finally {
                        preparationCancelled = true
                    }
                },
                onAnalysisComplete = {},
                onPreparationComplete = {}
            )
        }

        advanceTimeBy(1)
        job.cancel()
        job.join()

        assertTrue(analysisCancelled)
        assertTrue(preparationCancelled)
    }
}
