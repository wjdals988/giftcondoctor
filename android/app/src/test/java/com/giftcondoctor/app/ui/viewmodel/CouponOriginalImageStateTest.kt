package com.giftcondoctor.app.ui.viewmodel

import com.giftcondoctor.app.data.CouponImageFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CouponOriginalImageStateTest {
    @Test
    fun idleAndErrorCanStartDownload() {
        assertTrue(shouldStartOriginalImageLoad(CouponOriginalImageState.Idle, force = false))
        assertTrue(
            shouldStartOriginalImageLoad(
                CouponOriginalImageState.Error("실패"),
                force = false
            )
        )
    }

    @Test
    fun loadingAndReadyDoNotStartDuplicateDownload() {
        assertFalse(shouldStartOriginalImageLoad(CouponOriginalImageState.Loading, force = false))
        assertFalse(
            shouldStartOriginalImageLoad(
                CouponOriginalImageState.Ready(CouponImageFile(File("unused"), 1L)),
                force = false
            )
        )
    }

    @Test
    fun forceRefreshReplacesLoadingOrReadyRequest() {
        assertTrue(shouldStartOriginalImageLoad(CouponOriginalImageState.Loading, force = true))
        assertTrue(
            shouldStartOriginalImageLoad(
                CouponOriginalImageState.Ready(CouponImageFile(File("unused"), 1L)),
                force = true
            )
        )
    }

    @Test
    fun onlyActiveDownloadIsCancelledWhenDialogCloses() {
        assertTrue(shouldCancelOriginalImageLoad(CouponOriginalImageState.Loading))
        assertFalse(shouldCancelOriginalImageLoad(CouponOriginalImageState.Idle))
        assertFalse(shouldCancelOriginalImageLoad(CouponOriginalImageState.Error("실패")))
    }
}
