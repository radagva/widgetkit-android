package com.radagva.widgetkit.currency

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** Base provider: each subclass declares which currencies it shows (one column each). */
abstract class CurrencyWidgetProvider(val currencies: List<Currency>) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == CurrencyWidgets.ACTION_REFRESH) refreshAsync(context)
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        CurrencyWidgets.refresh(context, currencies) { pending.finish() }
    }
}

class DollarWidgetProvider : CurrencyWidgetProvider(listOf(Currency.USD))

class EuroWidgetProvider : CurrencyWidgetProvider(listOf(Currency.EUR))

class DollarEuroWidgetProvider : CurrencyWidgetProvider(listOf(Currency.USD, Currency.EUR))
