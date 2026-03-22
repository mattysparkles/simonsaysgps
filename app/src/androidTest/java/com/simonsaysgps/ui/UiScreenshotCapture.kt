package com.simonsaysgps.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

internal object UiScreenshotCapture {
    private const val RECORD_ARG = "recordScreenshots"
    private const val OUTPUT_DIR_NAME = "ui-test-snapshots"

    fun capture(node: SemanticsNodeInteraction, name: String) {
        val image = node.captureToImage()
        if (!InstrumentationRegistry.getArguments().getString(RECORD_ARG).toBoolean()) {
            return
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outputDir = File(context.getExternalFilesDir(null), OUTPUT_DIR_NAME).apply { mkdirs() }
        val outputFile = File(outputDir, "$name.png")
        FileOutputStream(outputFile).use { stream ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
