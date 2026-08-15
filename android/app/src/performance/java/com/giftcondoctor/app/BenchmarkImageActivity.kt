package com.giftcondoctor.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import com.giftcondoctor.app.ui.screens.CouponImageDialog
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.CouponOriginalImageState

class BenchmarkImageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GDTheme {
                val previewBitmap = remember { benchmarkBitmap().asImageBitmap() }
                CouponImageDialog(
                    previewBitmap = previewBitmap,
                    imageState = CouponOriginalImageState.Error("benchmark preview"),
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }
    }
}

private fun benchmarkBitmap(): Bitmap = Bitmap.createBitmap(600, 1_200, Bitmap.Config.ARGB_8888).apply {
    eraseColor(android.graphics.Color.WHITE)
}
