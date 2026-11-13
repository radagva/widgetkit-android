package com.radagva.widgetkit.colorpicker

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.SystemClock

/**
 * Mirrors the screen into an [ImageReader] and keeps the most recent frame, so a full-screen
 * [Bitmap] snapshot can be taken on demand.
 */
class ScreenCapture(
    private val projection: MediaProjection,
    private val handler: Handler,
    width: Int,
    height: Int,
    private var densityDpi: Int,
) {
    var width = width
        private set
    var height = height
        private set

    private var reader: ImageReader = newReader()
    private var display: VirtualDisplay = projection.createVirtualDisplay(
        "WidgetKitColorPicker",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
    ) ?: throw IllegalStateException("Could not create the screen capture display")

    private var latest: Image? = null

    /** Uptime (ms) when the latest frame arrived; frames only arrive when the screen changes. */
    var latestFrameAt: Long = 0
        private set

    @SuppressLint("WrongConstant") // RGBA_8888 is a valid ImageReader format for screen capture.
    private fun newReader(): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ r ->
                val image = try {
                    r.acquireLatestImage()
                } catch (_: IllegalStateException) {
                    null
                } ?: return@setOnImageAvailableListener
                latest?.close()
                latest = image
                latestFrameAt = SystemClock.uptimeMillis()
            }, handler)
        }

    /** Full-screen copy of the latest frame, or null if none arrived yet. */
    fun snapshot(): Bitmap? {
        val image = latest ?: return null
        return try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * image.width
            val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer.rewind())
            if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
        } catch (_: IllegalStateException) {
            null // image was closed underneath us
        }
    }

    /** Call when the screen size changes (rotation). */
    fun resize(newWidth: Int, newHeight: Int, newDensityDpi: Int) {
        if (newWidth == width && newHeight == height && newDensityDpi == densityDpi) return
        width = newWidth
        height = newHeight
        densityDpi = newDensityDpi
        latest?.close()
        latest = null
        val old = reader
        reader = newReader()
        display.resize(width, height, densityDpi)
        display.surface = reader.surface
        old.close()
    }

    fun release() {
        display.release()
        latest?.close()
        latest = null
        reader.close()
        projection.stop()
    }
}
