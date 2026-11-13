package com.radagva.widgetkit.calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.Calendar

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREV_MONTH, ACTION_NEXT_MONTH, ACTION_TODAY -> onNavigate(context, intent)
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        MonthNavigationStore.remove(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun onNavigate(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val now = System.currentTimeMillis()
        when (intent.action) {
            ACTION_PREV_MONTH -> MonthNavigationStore.shift(context, id, -1, now)
            ACTION_NEXT_MONTH -> MonthNavigationStore.shift(context, id, +1, now)
            ACTION_TODAY -> MonthNavigationStore.reset(context, id)
        }
        update(context, AppWidgetManager.getInstance(context), intArrayOf(id))
    }

    companion object {
        const val ACTION_PREV_MONTH = "com.radagva.widgetkit.calendar.PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.radagva.widgetkit.calendar.NEXT_MONTH"
        const val ACTION_TODAY = "com.radagva.widgetkit.calendar.TODAY"
        private const val ACTION_REFRESH = "com.radagva.widgetkit.calendar.REFRESH"

        private val NAV_ACTIONS = listOf(ACTION_PREV_MONTH, ACTION_NEXT_MONTH, ACTION_TODAY)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        /** Broadcast PendingIntent for a month-navigation tap on a specific widget instance. */
        fun navigationIntent(context: Context, appWidgetId: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // Unique per (widget, action) so the intents don't overwrite each other.
                appWidgetId * NAV_ACTIONS.size + NAV_ACTIONS.indexOf(action) + 1,
                Intent(context, CalendarWidgetProvider::class.java)
                    .setAction(action)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val now = System.currentTimeMillis()
            val hasPermission = CalendarEventsRepository.hasPermission(context)
            val events = CalendarEventsRepository.upcomingToday(context, now)

            ids.forEach { id ->
                val offset = MonthNavigationStore.offset(context, id, now)
                manager.updateAppWidget(
                    id,
                    CalendarWidgetRenderer.render(context, id, now, offset, hasPermission, events),
                )
            }
            scheduleNextRefresh(context, now, events)
        }

        /** Refresh at the next event end (so finished events drop off) or at midnight. */
        private fun scheduleNextRefresh(context: Context, now: Long, events: List<CalendarEvent>) {
            val midnight = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val nextEventEnd = events.filter { !it.allDay && it.end > now }.minOfOrNull { it.end }
            val triggerAt = minOf(midnight, nextEventEnd ?: midnight)

            context.getSystemService(AlarmManager::class.java)
                .set(AlarmManager.RTC, triggerAt, refreshIntent(context))
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CalendarWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
