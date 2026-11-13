package com.radagva.widgetkit.currency

import android.content.Context
import androidx.annotation.StringRes
import com.radagva.widgetkit.R

/** Which dolarapi sources ("fuente") the currency widgets show. */
enum class RateSources(@StringRes val label: Int, private val source: String?) {
    BOTH(R.string.sources_both, null),
    OFICIAL(R.string.sources_oficial, "oficial"),
    PARALELO(R.string.sources_paralelo, "paralelo");

    /** Filters rates by source; falls back to all rates if the API stops returning this one. */
    fun filter(rates: List<CurrencyRate>): List<CurrencyRate> {
        if (source == null) return rates
        return rates.filter { it.source.equals(source, ignoreCase = true) }.ifEmpty { rates }
    }
}

/** User options shared by all three currency widgets. */
data class CurrencyWidgetOptions(val sources: RateSources, val showChange: Boolean) {

    companion object {
        private const val PREFS = "currency_widget_options"
        private const val KEY_SOURCES = "sources"
        private const val KEY_SHOW_CHANGE = "show_change"

        fun load(context: Context): CurrencyWidgetOptions {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val sources = prefs.getString(KEY_SOURCES, null)
                ?.let { runCatching { RateSources.valueOf(it) }.getOrNull() } ?: RateSources.BOTH
            return CurrencyWidgetOptions(sources, prefs.getBoolean(KEY_SHOW_CHANGE, true))
        }

        fun save(context: Context, options: CurrencyWidgetOptions) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_SOURCES, options.sources.name)
                .putBoolean(KEY_SHOW_CHANGE, options.showChange)
                .apply()
        }
    }
}
