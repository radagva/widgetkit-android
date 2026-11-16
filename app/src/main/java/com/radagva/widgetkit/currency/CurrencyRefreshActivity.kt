package com.radagva.widgetkit.currency

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.radagva.widgetkit.theme.WidgetKind

/**
 * Invisible screen that runs a currency widget's refresh / retry button and closes at once.
 *
 * These buttons used to send a broadcast to the widget provider, but Xiaomi (MIUI/HyperOS) and
 * some other vendors drop broadcasts to apps whose process isn't running unless "Autostart" is
 * allowed, so the tap silently did nothing. Opening an activity from a widget tap is always
 * allowed. Same approach as the UUID widget's buttons.
 */
class CurrencyRefreshActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent?.data?.lastPathSegment?.let { runCatching { WidgetKind.valueOf(it) }.getOrNull() }
        val spec = CurrencyWidgets.specs.firstOrNull { it.kind == kind }
        if (spec != null) CurrencyWidgets.refresh(applicationContext, spec.currencies)
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
        fun pendingIntent(context: Context, kind: WidgetKind): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, CurrencyRefreshActivity::class.java)
                    // Distinct data per widget so their PendingIntents never merge.
                    .setData(Uri.parse("widgetkit://currency/refresh/${kind.name}"))
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_HISTORY,
                    ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
