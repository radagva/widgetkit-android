package com.radagva.widgetkit.colorpicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.sqrt

/**
 * A magnifying glass: a round lens showing the screen pixels around a point, enlarged into a
 * pixel grid, with the center pixel outlined, a ring in the picked color, and a handle.
 */
@SuppressLint("ViewConstructor")
class MagnifierView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    /** Source pixels shown across the lens (odd, so there's a single center pixel). */
    private val gridSize = 15
    private val lensDiameter = 128 * density
    private val ringWidth = 6 * density
    private val handleLength = 34 * density
    private val handleWidth = 10 * density

    /** Lens center inside this view (the handle extends towards the bottom-right). */
    val lensCenterX = ringWidth + lensDiameter / 2
    val lensCenterY = lensCenterX

    private val viewSize = (lensCenterX + (lensDiameter / 2 + ringWidth) / sqrt(2f) + handleLength).toInt() + 1

    private var snapshot: Bitmap? = null
    private var centerX = 0
    private var centerY = 0

    /** Color of the pixel at the lens center. */
    var pickedColor: Int = Color.GRAY
        private set

    private val cellPaint = Paint()
    private val gridPaint = Paint().apply { color = 0x22000000; strokeWidth = 1f }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ringWidth }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = 0xCC000000.toInt() }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * density }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = handleWidth
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2C2C2E.toInt()
    }
    private val handleEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = handleWidth + 2 * density
        strokeCap = Paint.Cap.ROUND
        color = 0x66FFFFFF
    }
    private val lensClip = Path()

    init {
        contentDescription = "Color picker magnifier"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewSize, viewSize)
    }

    /** Shows [bitmap] (full screen) around screen point ([x], [y]). */
    fun show(bitmap: Bitmap?, x: Int, y: Int) {
        snapshot = bitmap
        centerX = x
        centerY = y
        pickedColor = pixel(x, y)
        invalidate()
    }

    private fun pixel(x: Int, y: Int): Int {
        val bitmap = snapshot ?: return Color.GRAY
        if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) return Color.BLACK
        return bitmap.getPixel(x, y) or 0xFF000000.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = lensDiameter / 2
        val cell = lensDiameter / gridSize
        val left = lensCenterX - radius
        val top = lensCenterY - radius
        val half = gridSize / 2

        // Handle (drawn first so the lens sits on top of it).
        val angle = sqrt(0.5f)
        val startOffset = radius + ringWidth / 2
        val sx = lensCenterX + startOffset * angle
        val sy = lensCenterY + startOffset * angle
        val ex = sx + handleLength * angle
        val ey = sy + handleLength * angle
        canvas.drawLine(sx, sy, ex, ey, handleEdgePaint)
        canvas.drawLine(sx, sy, ex, ey, handlePaint)

        // Enlarged pixels, clipped to the lens.
        canvas.save()
        lensClip.reset()
        lensClip.addCircle(lensCenterX, lensCenterY, radius, Path.Direction.CW)
        canvas.clipPath(lensClip)
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                cellPaint.color = pixel(centerX + col - half, centerY + row - half)
                val l = left + col * cell
                val t = top + row * cell
                canvas.drawRect(l, t, l + cell, t + cell, cellPaint)
            }
        }
        for (i in 1 until gridSize) {
            canvas.drawLine(left + i * cell, top, left + i * cell, top + lensDiameter, gridPaint)
            canvas.drawLine(left, top + i * cell, left + lensDiameter, top + i * cell, gridPaint)
        }
        // Center pixel outline, in black or white depending on the picked color.
        centerPaint.color = if (isLight(pickedColor)) Color.BLACK else Color.WHITE
        val cl = left + half * cell
        val ct = top + half * cell
        canvas.drawRect(cl, ct, cl + cell, ct + cell, centerPaint)
        canvas.restore()

        // Ring in the picked color, with a thin dark border for contrast on any background.
        ringPaint.color = pickedColor
        canvas.drawCircle(lensCenterX, lensCenterY, radius + ringWidth / 2, ringPaint)
        canvas.drawCircle(lensCenterX, lensCenterY, radius + ringWidth, borderPaint)
        canvas.drawCircle(lensCenterX, lensCenterY, radius, borderPaint)
    }

    companion object {
        fun isLight(color: Int): Boolean {
            val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            return luminance > 150
        }

        fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
    }
}
