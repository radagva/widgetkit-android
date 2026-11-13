package com.radagva.widgetkit

import android.app.Application
import com.radagva.widgetkit.calendar.CalendarWidgetProvider
import com.radagva.widgetkit.currency.CurrencyWidgets
import com.radagva.widgetkit.uuid.UuidWidgets

class WidgetKitApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // A force-stop (which some vendors, e.g. Xiaomi, do when clearing recents or saving
        // battery) cancels every PendingIntent the widgets' buttons rely on, so the buttons
        // stop responding. Redrawing the widgets whenever the app starts gives them fresh ones.
        refreshWidgetViews()
    }

    private fun refreshWidgetViews() {
        runCatching { CalendarWidgetProvider.updateAll(this) }
        runCatching { CurrencyWidgets.updateAll(this) } // redraw from stored rates, no fetch
        runCatching { UuidWidgets.updateAll(this) }
    }
}
