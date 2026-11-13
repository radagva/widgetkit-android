package com.radagva.widgetkit.colorpicker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.radagva.widgetkit.R

/**
 * Floating color picker: a draggable magnifier overlay that reads the screen through
 * MediaProjection. Runs as a foreground service while the Quick Settings tile is on.
 *
 * Screen capture also captures our own overlay, so the magnifier zooms into a clean snapshot
 * taken with the overlay hidden (when it starts, and each time the magnifier is released).
 */
class ColorPickerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    private var capture: ScreenCapture? = null
    private var snapshot: Bitmap? = null

    /** The magnifier window; the copy button lives in its own window so it can stay on screen. */
    private var overlay: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private var buttonAdded = false
    private lateinit var buttonParams: WindowManager.LayoutParams
    private lateinit var magnifier: MagnifierView
    private lateinit var copyButton: TextView

    /** Screen point under the lens center. */
    private var targetX = 0
    private var targetY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14+: must be in the foreground (mediaProjection type) before getMediaProjection.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        if (capture != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        val projection = data?.let {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, it)
        }
        if (projection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Stopped from the system's screen-sharing indicator, or another app took over capture.
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, handler)

        val metrics = screenMetrics()
        capture = runCatching {
            ScreenCapture(projection, handler, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }.getOrNull()
        if (capture == null) {
            projection.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        ColorPickerTileService.requestUpdate(this)

        // Let the consent dialog and Quick Settings panel disappear before the first snapshot.
        handler.postDelayed({
            targetX = metrics.widthPixels / 2
            targetY = metrics.heightPixels / 2
            refreshSnapshot(firstTime = true)
        }, FIRST_SNAPSHOT_DELAY_MS)
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = screenMetrics()
        capture?.resize(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        targetX = targetX.coerceIn(0, metrics.widthPixels - 1)
        targetY = targetY.coerceIn(0, metrics.heightPixels - 1)
        if (overlay != null) {
            moveTo(targetX, targetY)
            handler.postDelayed({ refreshSnapshot() }, ROTATION_SETTLE_MS)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        if (buttonAdded) runCatching { windowManager.removeView(copyButton) }
        buttonAdded = false
        capture?.release()
        capture = null
        isRunning = false
        ColorPickerTileService.requestUpdate(this)
        super.onDestroy()
    }

    // ───────────── Overlay ─────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val density = resources.displayMetrics.density
        magnifier = MagnifierView(this)
        copyButton = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            compoundDrawablePadding = (8 * density).toInt()
            // Pill with a ripple so a press is visibly highlighted.
            val pill = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(0xE61C1C1E.toInt())
                setStroke(1, 0x33FFFFFF)
            }
            background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), pill, null)
            setOnClickListener { copyColor() }
        }
        overlay = magnifier
        params = overlayParams()
        buttonParams = overlayParams()

        var downRawX = 0f
        var downRawY = 0f
        var downTargetX = 0
        var downTargetY = 0
        magnifier.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downTargetX = targetX
                    downTargetY = targetY
                    setCopyButtonVisible(false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    moveTo(
                        downTargetX + (event.rawX - downRawX).toInt(),
                        downTargetY + (event.rawY - downRawY).toInt(),
                    )
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Dragging stopped: take a fresh snapshot (the screen may have changed),
                    // then show the copy button with the color under the lens.
                    refreshSnapshot(showCopyButton = true)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlay, params)
        moveTo(targetX, targetY)
        setCopyButtonVisible(true)
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** Moves the overlay so the lens center sits on screen point ([x], [y]). */
    private fun moveTo(x: Int, y: Int) {
        val c = capture ?: return
        targetX = x.coerceIn(0, c.width - 1)
        targetY = y.coerceIn(0, c.height - 1)
        params.x = targetX - magnifier.lensCenterX.toInt()
        params.y = targetY - magnifier.lensCenterY.toInt()
        overlay?.let { windowManager.updateViewLayout(it, params) }
        magnifier.show(snapshot, targetX, targetY)
        updateCopyButton()
    }

    private fun setCopyButtonVisible(visible: Boolean) {
        if (!::copyButton.isInitialized) return
        if (!visible) {
            if (buttonAdded) {
                runCatching { windowManager.removeView(copyButton) }
                buttonAdded = false
            }
            return
        }
        updateCopyButton()
        positionCopyButton()
        if (buttonAdded) {
            windowManager.updateViewLayout(copyButton, buttonParams)
        } else {
            windowManager.addView(copyButton, buttonParams)
            buttonAdded = true
        }
    }

    /** Below the lens, centered on it and kept on screen; above the lens near the bottom edge. */
    private fun positionCopyButton() {
        val c = capture ?: return
        val margin = (8 * resources.displayMetrics.density).toInt()
        copyButton.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val w = copyButton.measuredWidth
        val h = copyButton.measuredHeight
        val lensReach = magnifier.lensCenterX.toInt()
        buttonParams.x = (targetX - w / 2).coerceIn(margin, maxOf(margin, c.width - w - margin))
        val below = targetY + lensReach + margin
        buttonParams.y = if (below + h <= c.height - margin) below else (targetY - lensReach - margin - h).coerceAtLeast(margin)
    }

    private fun updateCopyButton() {
        val color = magnifier.pickedColor
        val density = resources.displayMetrics.density
        val swatch = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((1 * density).toInt(), 0x99FFFFFF.toInt())
            setSize((14 * density).toInt(), (14 * density).toInt())
        }
        copyButton.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch, null, null, null)
        copyButton.text = getString(R.string.color_picker_copy, MagnifierView.hex(color))
    }

    private fun copyColor() {
        val hex = MagnifierView.hex(magnifier.pickedColor)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(hex, hex))

        // Feedback: haptic tick, a quick press bounce, and "Copied ✓" for a moment.
        copyButton.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY,
        )
        copyButton.animate().cancel()
        copyButton.scaleX = 0.9f
        copyButton.scaleY = 0.9f
        copyButton.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator(3f)).start()
        copyButton.text = getString(R.string.color_picker_copied_button, hex)
        relayoutCopyButton()
        handler.removeCallbacks(restoreCopyLabel)
        handler.postDelayed(restoreCopyLabel, COPIED_LABEL_MS)

        // Android 13+ shows its own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, getString(R.string.color_picker_copied, hex), Toast.LENGTH_SHORT).show()
        }
    }

    private val restoreCopyLabel = Runnable {
        updateCopyButton()
        relayoutCopyButton()
    }

    /** The label width changes ("Copy" / "Copied ✓"); keep the button centered under the lens. */
    private fun relayoutCopyButton() {
        if (!buttonAdded) return
        positionCopyButton()
        windowManager.updateViewLayout(copyButton, buttonParams)
    }

    // ───────────── Snapshots ─────────────

    /**
     * Hides the overlay, waits for a frame rendered without it, takes the snapshot, and shows
     * the overlay again. On the first call the overlay is created afterwards.
     */
    private fun refreshSnapshot(firstTime: Boolean = false, showCopyButton: Boolean = false) {
        val c = capture ?: return
        val hiddenAt = SystemClock.uptimeMillis()
        overlay?.visibility = View.INVISIBLE
        val buttonWasShown = buttonAdded
        if (buttonWasShown) copyButton.visibility = View.INVISIBLE

        var waited = 0L
        val take = object : Runnable {
            override fun run() {
                // Wait for a frame produced after hiding (or give up after a short while).
                if (c.latestFrameAt <= hiddenAt && waited < MAX_FRAME_WAIT_MS) {
                    waited += FRAME_POLL_MS
                    handler.postDelayed(this, FRAME_POLL_MS)
                    return
                }
                // Not recycling the old bitmap: the magnifier may still draw it for a frame.
                c.snapshot()?.let { snapshot = it }
                if (firstTime && overlay == null) {
                    addOverlay()
                } else {
                    overlay?.visibility = View.VISIBLE
                    magnifier.show(snapshot, targetX, targetY)
                    updateCopyButton()
                    if (buttonWasShown) copyButton.visibility = View.VISIBLE
                    if (showCopyButton) setCopyButtonVisible(true)
                }
            }
        }
        handler.postDelayed(take, if (overlay == null) 0 else HIDE_SETTLE_MS)
    }

    // ───────────── Helpers ─────────────

    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics
    }

    @Suppress("DEPRECATION")
    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.color_picker_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ColorPickerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_color_picker)
            .setContentTitle(getString(R.string.color_picker_notification_title))
            .setContentText(getString(R.string.color_picker_notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.color_picker_stop), stop)
            .build()
    }

    companion object {
        /** Read by the Quick Settings tile to show its on/off state. */
        @Volatile
        var isRunning = false
            private set

        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "com.radagva.widgetkit.colorpicker.STOP"
        private const val CHANNEL_ID = "color_picker"
        private const val NOTIFICATION_ID = 42

        private const val FIRST_SNAPSHOT_DELAY_MS = 450L
        private const val COPIED_LABEL_MS = 1000L
        private const val HIDE_SETTLE_MS = 50L
        private const val FRAME_POLL_MS = 16L
        private const val MAX_FRAME_WAIT_MS = 250L
        private const val ROTATION_SETTLE_MS = 400L

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ColorPickerService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ColorPickerService::class.java))
        }
    }
}
