package com.giftcondoctor.app.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

internal data class ParallelImageProcessingResult<Analysis, Preparation>(
    val analysis: Result<Analysis>,
    val preparation: Result<Preparation>
)

/**
 * Runs user-visible image analysis and upload preparation independently.
 * Each callback is delivered as soon as its own work completes, while parent cancellation
 * still cancels both children.
 */
internal suspend fun <Analysis, Preparation> processImageSelectionInParallel(
    analyze: suspend () -> Analysis,
    prepare: suspend () -> Preparation,
    onAnalysisComplete: (Result<Analysis>) -> Unit,
    onPreparationComplete: (Result<Preparation>) -> Unit
): ParallelImageProcessingResult<Analysis, Preparation> = supervisorScope {
    val analysis = async {
        captureNonCancellationFailure(analyze).also(onAnalysisComplete)
    }
    val preparation = async {
        captureNonCancellationFailure(prepare).also(onPreparationComplete)
    }
    ParallelImageProcessingResult(
        analysis = analysis.await(),
        preparation = preparation.await()
    )
}

private suspend fun <T> captureNonCancellationFailure(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
