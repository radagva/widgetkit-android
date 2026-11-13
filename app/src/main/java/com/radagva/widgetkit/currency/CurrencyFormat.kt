package com.radagva.widgetkit.currency

import android.content.Context
import android.text.format.DateUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Formatting shared by the currency widgets and the calculator. */
object CurrencyFormat {

    private val ISO_PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")

    /** Localized name of a dolarapi source; unknown sources are shown as the API sends them. */
    fun sourceLabel(context: Context, source: String, locale: Locale = Locale.getDefault()): String =
        when (source.lowercase(Locale.ROOT)) {
            "oficial" -> context.getString(com.radagva.widgetkit.R.string.sources_oficial)
            "paralelo" -> context.getString(com.radagva.widgetkit.R.string.sources_paralelo)
            else -> source.replaceFirstChar { it.titlecase(locale) }
        }

    /** Two-decimal number format for the given locale. */
    fun amount(locale: Locale = Locale.getDefault()): NumberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    /** "08:01" today, "12 nov, 08:01" otherwise. */
    fun time(context: Context, time: Long): String {
        val flags = if (DateUtils.isToday(time)) DateUtils.FORMAT_SHOW_TIME
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
        return DateUtils.formatDateTime(context, time, flags)
    }

    /** Most recent fechaActualizacion across the rates, or null if none parse. */
    fun latestUpdate(rates: List<CurrencyRate>): Long? = rates.mapNotNull { parseIso(it.updatedAt) }.maxOrNull()

    fun parseIso(value: String): Long? {
        // Normalize fractional seconds to exactly 3 digits so SSS parses them as millis.
        val normalized = value.replace(Regex("""\.(\d{1,3})\d*""")) { "." + it.groupValues[1].padEnd(3, '0') }
        return ISO_PATTERNS.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(normalized)?.time }.getOrNull()
        }
    }
}
