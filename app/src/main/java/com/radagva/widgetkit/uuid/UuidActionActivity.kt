package com.radagva.widgetkit.uuid

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Invisible screen that runs the UUID widget's buttons (generate / copy) and closes at once.
 *
 * Widget buttons usually send a broadcast, but Xiaomi (MIUI/HyperOS) and some other vendors
 * block broadcasts to apps whose process isn't running unless "Autostart" is allowed, so the
 * buttons silently did nothing. Opening an activity from a widget tap is always allowed.
 */
class UuidActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        when (intent?.action) {
            ACTION_GENERATE -> {
                UuidStore.generate(app)
                UuidWidgets.updateAll(app)
            }
            ACTION_COPY -> {
                UuidWidgets.copy(app, UuidStore.state(app).value)
                // "Copied ✓" on the widget for a moment, then back to the description.
                UuidWidgets.updateAll(app, copied = true)
                Handler(Looper.getMainLooper()).postDelayed({ UuidWidgets.updateAll(app) }, COPIED_MS)
            }
        }
        finish()
        // No open/close animation: nothing should appear to happen besides the widget updating.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val ACTION_GENERATE = "com.radagva.widgetkit.uuid.action.GENERATE"
        const val ACTION_COPY = "com.radagva.widgetkit.uuid.action.COPY"
        private const val COPIED_MS = 1_500L

        fun intent(context: Context, action: String): Intent =
            Intent(context, UuidActionActivity::class.java)
                .setAction(action)
                // Distinct data so the generate and copy PendingIntents never merge.
                .setData(Uri.parse("widgetkit://uuid/$action"))
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY,
                )
    }
}
