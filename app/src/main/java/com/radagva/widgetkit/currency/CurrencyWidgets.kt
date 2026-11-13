package com.radagva.widgetkit.currency

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.radagva.widgetkit.theme.WidgetKind
import com.radagva.widgetkit.theme.WidgetSettings
import java.util.concurrent.Executors

/** Coordinates fetching and redrawing for all three currency widgets. */
object CurrencyWidgets {

    const val ACTION_REFRESH = "com.radagva.widgetkit.currency.REFRESH"

    /** A currency widget: its provider, the currencies it shows (one column each), its settings key. */
    data class Spec(
        val provider: Class<out CurrencyWidgetProvider>,
        val currencies: List<Currency>,
        val kind: WidgetKind,
    )

    val specs: List<Spec> = listOf(
        Spec(DollarWidgetProvider::class.java, listOf(Currency.USD), WidgetKind.USD),
        Spec(EuroWidgetProvider::class.java, listOf(Currency.EUR), WidgetKind.EUR),
        Spec(DollarEuroWidgetProvider::class.java, listOf(Currency.USD, Currency.EUR), WidgetKind.BOTH),
    )

    fun spec(kind: WidgetKind): Spec = specs.first { it.kind == kind }

    /** Renders a currency widget with its own scheme and options (also used for app previews). */
    fun render(context: Context, spec: Spec): RemoteViews = CurrencyWidgetRenderer.render(
        context = context,
        provider = spec.provider,
        currencies = spec.currencies,
        theme = WidgetSettings.theme(context, spec.kind),
        options = CurrencyWidgetOptions.load(context, spec.kind),
    )

    private val executor = Executors.newCachedThreadPool()

    /** Redraws every currency widget from the stored state. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        specs.forEach { spec ->
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.provider))
            if (ids.isEmpty()) return@forEach
            val views = render(context, spec)
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
