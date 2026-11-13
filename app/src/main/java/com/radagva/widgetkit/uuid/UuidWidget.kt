package com.radagva.widgetkit.uuid

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.StringRes
import com.radagva.widgetkit.MainActivity
import com.radagva.widgetkit.R
import com.radagva.widgetkit.theme.WidgetKind
import com.radagva.widgetkit.theme.WidgetSettings
import com.radagva.widgetkit.theme.WidgetTheme

@get:StringRes
val UuidVersion.description: Int
    get() = when (this) {
        UuidVersion.V1 -> R.string.uuid_v1
        UuidVersion.V4 -> R.string.uuid_v4
        UuidVersion.V6 -> R.string.uuid_v6
        UuidVersion.V7 -> R.string.uuid_v7
    }

/** Shows the current UUID with generate and copy buttons. */
class UuidWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        UuidWidgets.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_GENERATE -> {
                UuidStore.generate(context)
                UuidWidgets.updateAll(context)
            }
            ACTION_COPY -> {
                val value = UuidStore.state(context).value
                UuidWidgets.copy(context, value)
                // Show "Copied ✓" on the widget for a moment, then go back to the description.
                UuidWidgets.updateAll(context, copied = true)
                val pending = goAsync()
                Handler(Looper.getMainLooper()).postDelayed({
                    UuidWidgets.updateAll(context)
                    pending.finish()
                }, COPIED_MS)
            }
        }
    }

    companion object {
        const val ACTION_GENERATE = "com.radagva.widgetkit.uuid.GENERATE"
        const val ACTION_COPY = "com.radagva.widgetkit.uuid.COPY"
        private const val COPIED_MS = 1_500L
    }
}

object UuidWidgets {

    fun updateAll(context: Context, copied: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, UuidWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = render(context, copied = copied)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun render(
        context: Context,
        theme: WidgetTheme = WidgetSettings.theme(context, WidgetKind.UUID),
        state: UuidStore.State = UuidStore.state(context),
        copied: Boolean = false,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_uuid)
        theme.tint(views, R.id.widget_bg) { it.background }
        theme.textColor(views, R.id.uuid_title) { it.accent }
        theme.textColor(views, R.id.uuid_value) { it.label }
        theme.tint(views, R.id.uuid_generate) { it.accent }
        theme.tint(views, R.id.uuid_copy) { it.accent }

        views.setTextViewText(R.id.uuid_title, context.getString(R.string.uuid_title, state.version.number))
        views.setTextViewText(R.id.uuid_value, state.value)
        if (copied) {
            views.setTextViewText(R.id.uuid_status, context.getString(R.string.uuid_copied))
            theme.textColor(views, R.id.uuid_status) { it.positive }
        } else {
            views.setTextViewText(R.id.uuid_status, context.getString(state.version.description))
            theme.textColor(views, R.id.uuid_status) { it.secondaryLabel }
        }

        views.setOnClickPendingIntent(R.id.uuid_generate, broadcast(context, UuidWidgetProvider.ACTION_GENERATE, 1))
        views.setOnClickPendingIntent(R.id.uuid_copy, broadcast(context, UuidWidgetProvider.ACTION_COPY, 2))
        views.setOnClickPendingIntent(
            R.id.uuid_content,
            PendingIntent.getActivity(
                context,
                3,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return views
    }

    /** Copies [value]; Android 13+ shows its own confirmation, older versions get a toast. */
    fun copy(context: Context, value: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("UUID", value))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.uuid_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, UuidWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
