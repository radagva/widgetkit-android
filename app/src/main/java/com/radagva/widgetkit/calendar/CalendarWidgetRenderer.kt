package com.radagva.widgetkit.calendar

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import com.radagva.widgetkit.R
import com.radagva.widgetkit.theme.WidgetSettings
import com.radagva.widgetkit.theme.WidgetTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Builds the RemoteViews for the two-column calendar widget. */
object CalendarWidgetRenderer {

    private const val MAX_VISIBLE_EVENTS = 3

    fun render(
        context: Context,
        appWidgetId: Int,
        now: Long,
        monthOffset: Int,
        hasPermission: Boolean,
        events: List<CalendarEvent>,
        theme: WidgetTheme = WidgetSettings.theme(context, WidgetSettings.CALENDAR),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calendar)
        theme.tint(views, R.id.widget_bg) { it.background }
        theme.textColor(views, R.id.month_title) { it.accent }
        theme.textColor(views, R.id.events_title) { it.accent }
        theme.textColor(views, R.id.events_message) { it.secondaryLabel }
        theme.textColor(views, R.id.events_more) { it.secondaryLabel }
        theme.tint(views, R.id.month_prev) { it.accent }
        theme.tint(views, R.id.month_next) { it.accent }
        val locale = Locale.getDefault()
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val displayedMonth = (today.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }

        renderMonth(context, views, theme, today, displayedMonth, locale)
        renderEvents(context, views, theme, today, locale, hasPermission, events)

        views.setOnClickPendingIntent(
            R.id.month_prev,
            CalendarWidgetProvider.navigationIntent(context, appWidgetId, CalendarWidgetProvider.ACTION_PREV_MONTH),
        )
        views.setOnClickPendingIntent(
            R.id.month_next,
            CalendarWidgetProvider.navigationIntent(context, appWidgetId, CalendarWidgetProvider.ACTION_NEXT_MONTH),
        )
        views.setOnClickPendingIntent(
            R.id.month_title,
            CalendarWidgetProvider.navigationIntent(context, appWidgetId, CalendarWidgetProvider.ACTION_TODAY),
        )
        val openAt = if (monthOffset == 0) now else displayedMonth.timeInMillis
        views.setOnClickPendingIntent(R.id.month_grid, openCalendarIntent(context, appWidgetId, openAt))
        return views
    }

    private fun renderMonth(
        context: Context,
        views: RemoteViews,
        theme: WidgetTheme,
        today: Calendar,
        month: Calendar,
        locale: Locale,
    ) {
        val sameYear = month.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val titlePattern = if (sameYear) "MMMM" else "MMMM yyyy"
        views.setTextViewText(
            R.id.month_title,
            SimpleDateFormat(titlePattern, locale).format(month.time).uppercase(locale),
        )

        val firstDayOfWeek = today.firstDayOfWeek
        val isWeekend = { dow: Int -> dow == Calendar.SATURDAY || dow == Calendar.SUNDAY }

        // Weekday initials (S M T W T F S), respecting the locale's first day of week.
        views.removeAllViews(R.id.weekday_row)
        val narrowDay = SimpleDateFormat("EEEEE", locale)
        val probe = today.clone() as Calendar
        for (i in 0 until 7) {
            val dow = (firstDayOfWeek - 1 + i) % 7 + 1
            probe.set(Calendar.DAY_OF_WEEK, dow)
            val cell = RemoteViews(context.packageName, R.layout.widget_calendar_weekday)
            cell.setTextViewText(R.id.weekday, narrowDay.format(probe.time))
            theme.textColor(cell, R.id.weekday) { it.secondaryLabel }
            views.addView(R.id.weekday_row, cell)
        }

        // Day grid. `month` is already set to the 1st of the displayed month.
        val leadingBlanks = (month.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val isCurrentMonth = sameYear && month.get(Calendar.MONTH) == today.get(Calendar.MONTH)
        // null when another month is displayed (never collides with overflow day numbers).
        val todayDay: Int? = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else null
        val weeks = (leadingBlanks + daysInMonth + 6) / 7
        val daysInPrevMonth = (month.clone() as Calendar)
            .apply { add(Calendar.MONTH, -1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)

        views.removeAllViews(R.id.month_grid)
        for (week in 0 until weeks) {
            val row = RemoteViews(context.packageName, R.layout.widget_calendar_week)
            for (col in 0 until 7) {
                val day = week * 7 + col - leadingBlanks + 1
                val layout = if (day == todayDay) R.layout.widget_calendar_day_today
                else R.layout.widget_calendar_day
                val cell = RemoteViews(context.packageName, layout)
                when {
                    day == todayDay -> {
                        cell.setTextViewText(R.id.day, day.toString())
                        theme.tint(cell, R.id.day_circle) { it.accent }
                        theme.textColor(cell, R.id.day) { it.onAccent }
                    }
                    day in 1..daysInMonth -> {
                        cell.setTextViewText(R.id.day, day.toString())
                        val weekend = isWeekend((firstDayOfWeek - 1 + col) % 7 + 1)
                        theme.textColor(cell, R.id.day) {
                            if (weekend) it.secondaryLabel else it.label
                        }
                    }
                    else -> {
                        // Overflow days from the previous / next month, dimmed.
                        val overflowDay = if (day < 1) daysInPrevMonth + day else day - daysInMonth
                        cell.setTextViewText(R.id.day, overflowDay.toString())
                        theme.textColor(cell, R.id.day) { it.mutedLabel }
                    }
                }
                row.addView(R.id.week_row, cell)
            }
            views.addView(R.id.month_grid, row)
        }
    }

    private fun renderEvents(
        context: Context,
        views: RemoteViews,
        theme: WidgetTheme,
        today: Calendar,
        locale: Locale,
        hasPermission: Boolean,
        events: List<CalendarEvent>,
    ) {
        views.setTextViewText(
            R.id.events_title,
            SimpleDateFormat("EEEE", locale).format(today.time).uppercase(locale),
        )
        views.removeAllViews(R.id.events_list)
        views.setViewVisibility(R.id.events_more, View.GONE)
        views.setViewVisibility(R.id.events_message, View.GONE)

        if (!hasPermission) {
            views.setViewVisibility(R.id.events_message, View.VISIBLE)
            views.setTextViewText(R.id.events_message, context.getString(R.string.calendar_widget_grant_access))
            views.setOnClickPendingIntent(
                R.id.events_message,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, CalendarPermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return
        }

        if (events.isEmpty()) {
            views.setViewVisibility(R.id.events_message, View.VISIBLE)
            views.setTextViewText(R.id.events_message, context.getString(R.string.calendar_widget_no_events))
            return
        }

        events.take(MAX_VISIBLE_EVENTS).forEach { event ->
            val item = RemoteViews(context.packageName, R.layout.widget_calendar_event)
            item.setInt(R.id.event_bar, "setColorFilter", event.color)
            item.setTextViewText(
                R.id.event_title,
                event.title.ifBlank { context.getString(R.string.calendar_widget_untitled) },
            )
            item.setTextViewText(R.id.event_time, formatTime(context, event))
            theme.textColor(item, R.id.event_title) { it.label }
            theme.textColor(item, R.id.event_time) { it.secondaryLabel }
            item.setOnClickPendingIntent(R.id.event_root, openEventIntent(context, event))
            views.addView(R.id.events_list, item)
        }

        val hidden = events.size - MAX_VISIBLE_EVENTS
        if (hidden > 0) {
            views.setViewVisibility(R.id.events_more, View.VISIBLE)
            views.setTextViewText(
                R.id.events_more,
                context.resources.getQuantityString(R.plurals.calendar_widget_more_events, hidden, hidden),
            )
        }
    }

    private fun formatTime(context: Context, event: CalendarEvent): String =
        if (event.allDay) context.getString(R.string.calendar_widget_all_day)
        else DateUtils.formatDateRange(context, event.begin, event.end, DateUtils.FORMAT_SHOW_TIME)

    private fun openCalendarIntent(context: Context, appWidgetId: Int, time: Long): PendingIntent {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(time.toString())
            .build()
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(Intent.ACTION_VIEW, uri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openEventIntent(context: Context, event: CalendarEvent): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id),
        )
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        return PendingIntent.getActivity(
            context,
            event.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
