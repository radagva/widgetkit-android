package com.radagva.widgetkit.currency

import android.content.Context
import androidx.annotation.StringRes
import com.radagva.widgetkit.R
import com.radagva.widgetkit.theme.WidgetKind

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

/** Options of one currency widget (Dollar, Euro, or Dollar & Euro). */
data class CurrencyWidgetOptions(val sources: RateSources, val showChange: Boolean) {

    companion object {
        private const val PREFS = "currency_widget_options"

        // Earlier versions shared one set of options across all currency widgets; those values
        // become each widget's default.
        private const val LEGACY_SOURCES = "sources"
        private const val LEGACY_SHOW_CHANGE = "show_change"

        fun load(context: Context, kind: WidgetKind): CurrencyWidgetOptions {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val sources = (prefs.getString(sourcesKey(kind), null) ?: prefs.getString(LEGACY_SOURCES, null))
                ?.let { runCatching { RateSources.valueOf(it) }.getOrNull() } ?: RateSources.BOTH
            val showChange = prefs.getBoolean(showChangeKey(kind), prefs.getBoolean(LEGACY_SHOW_CHANGE, true))
            return CurrencyWidgetOptions(sources, showChange)
        }

        fun save(context: Context, kind: WidgetKind, options: CurrencyWidgetOptions) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(sourcesKey(kind), options.sources.name)
                .putBoolean(showChangeKey(kind), options.showChange)
                .apply()
        }

        private fun sourcesKey(kind: WidgetKind) = "sources_${kind.name}"
        private fun showChangeKey(kind: WidgetKind) = "show_change_${kind.name}"
    }
}
