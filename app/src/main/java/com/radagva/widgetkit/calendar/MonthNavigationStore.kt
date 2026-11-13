package com.radagva.widgetkit.calendar

import android.content.Context
import java.util.Calendar

/**
 * Remembers, per widget instance, how many months away from the current month the user
 * has navigated. Navigation only lasts for the day it was made on; the next day the widget
 * snaps back to the current month.
 */
object MonthNavigationStore {

    private const val PREFS = "calendar_widget_navigation"

    fun offset(context: Context, appWidgetId: Int, now: Long): Int {
        val prefs = prefs(context)
        return if (prefs.getInt(dayKey(appWidgetId), -1) == dayStamp(now)) {
            prefs.getInt(offsetKey(appWidgetId), 0)
        } else {
            0
        }
    }

    fun shift(context: Context, appWidgetId: Int, delta: Int, now: Long) {
        val newOffset = offset(context, appWidgetId, now) + delta
        prefs(context).edit()
            .putInt(offsetKey(appWidgetId), newOffset)
            .putInt(dayKey(appWidgetId), dayStamp(now))
            .apply()
    }

    fun reset(context: Context, appWidgetId: Int) = remove(context, intArrayOf(appWidgetId))

    fun remove(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        appWidgetIds.forEach { editor.remove(offsetKey(it)).remove(dayKey(it)) }
        editor.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun offsetKey(id: Int) = "offset_$id"
    private fun dayKey(id: Int) = "day_$id"

    private fun dayStamp(now: Long): Int = Calendar.getInstance().run {
        timeInMillis = now
        get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    }
}
