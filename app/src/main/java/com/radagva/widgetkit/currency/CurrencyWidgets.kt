package com.radagva.widgetkit.currency

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.radagva.widgetkit.theme.WidgetSettings
import java.util.concurrent.Executors

/** Coordinates fetching and redrawing for all three currency widgets. */
object CurrencyWidgets {

    const val ACTION_REFRESH = "com.radagva.widgetkit.currency.REFRESH"

    val providers: List<Pair<Class<out CurrencyWidgetProvider>, List<Currency>>> = listOf(
        DollarWidgetProvider::class.java to listOf(Currency.USD),
        EuroWidgetProvider::class.java to listOf(Currency.EUR),
        DollarEuroWidgetProvider::class.java to listOf(Currency.USD, Currency.EUR),
    )

    private val executor = Executors.newCachedThreadPool()

    /** Redraws every currency widget from the stored state. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val theme = WidgetSettings.theme(context, WidgetSettings.CURRENCY)
        providers.forEach { (provider, currencies) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) return@forEach
            val views = CurrencyWidgetRenderer.render(context, provider, currencies, theme)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }

    /**
     * Marks [currencies] as refreshing, fetches them in parallel and redraws all widgets.
     * [onDone] runs on a background thread once everything is stored.
     */
    fun refresh(context: Context, currencies: Collection<Currency>, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        CurrencyStore.setRefreshing(app, currencies)
        updateAll(app)
        executor.execute {
            try {
                currencies
                    .map { currency -> currency to executor.submit<FetchResult> { CurrencyApi.fetch(app, currency) } }
                    .forEach { (currency, future) -> CurrencyStore.save(app, currency, future.get()) }
                updateAll(app)
            } finally {
                onDone()
            }
        }
    }
}
