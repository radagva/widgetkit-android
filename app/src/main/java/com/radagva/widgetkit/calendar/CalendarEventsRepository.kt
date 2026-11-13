package com.radagva.widgetkit.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Reads today's events from the device calendar provider. */
object CalendarEventsRepository {

    private const val EPOCH_JULIAN_DAY = 2440588

    private val PROJECTION = arrayOf(
        Instances.EVENT_ID,
        Instances.TITLE,
        Instances.BEGIN,
        Instances.END,
        Instances.ALL_DAY,
        Instances.DISPLAY_COLOR,
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Events for the local "today" that haven't ended yet. All-day events come first,
     * then timed events ordered by start time.
     */
    fun upcomingToday(context: Context, now: Long = System.currentTimeMillis()): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()

        // Querying by Julian day (instead of millis) makes the provider treat all-day
        // events (stored in UTC) correctly for the local time zone.
        val offset = TimeZone.getDefault().getOffset(now)
        val julianDay = TimeUnit.MILLISECONDS.toDays(now + offset) + EPOCH_JULIAN_DAY
        val uri = Instances.CONTENT_BY_DAY_URI.buildUpon()
            .appendPath(julianDay.toString())
            .appendPath(julianDay.toString())
            .build()

        val events = mutableListOf<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri,
                PROJECTION,
                "${Instances.VISIBLE} = 1",
                null,
                "${Instances.ALL_DAY} DESC, ${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val event = CalendarEvent(
                        id = c.getLong(0),
                        title = c.getString(1).orEmpty(),
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) != 0,
                        color = c.getInt(5) or 0xFF000000.toInt(),
                    )
                    if (event.allDay || event.end > now) events += event
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return events
    }
}
