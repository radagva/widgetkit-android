package com.radagva.widgetkit.currency

/** Official rate scraped from the bcv.org.ve home page. */
data class BcvRate(val value: Double, val valueDate: String?)

/**
 * Extracts the official rates from the bcv.org.ve home page. Each rate sits in a block like
 * `<div id="dolar"> ... <strong class="strong-tb"> 914,02180000</strong>`, and the page has one
 * "Fecha Valor" `<span property="dc:date" content="2026-11-16T00:00:00-04:00">` for all of them.
 */
object BcvParser {

    private val VALUE = Regex("""<strong[^>]*>\s*([\d.,]+)\s*</strong>""")
    private val DATE_TAG = Regex("""<span[^>]*property="dc:date"[^>]*>""")
    private val CONTENT_ATTR = Regex("""content="([^"]+)"""")

    /** Throws [CurrencyParseException] if the page doesn't have the expected shape. */
    fun parse(html: String, currency: Currency): BcvRate {
        val start = html.indexOf("id=\"${currency.bcvId}\"")
        if (start < 0) throw CurrencyParseException("No '${currency.bcvId}' block")
        val raw = VALUE.find(html, start)?.groupValues?.get(1)
            ?: throw CurrencyParseException("No value in '${currency.bcvId}' block")
        return BcvRate(parseNumber(raw), valueDate(html))
    }

    /** "1.010,56078251" -> 1010.56078251 (Venezuelan format: dot thousands, comma decimals). */
    internal fun parseNumber(raw: String): Double {
        val value = raw.replace(".", "").replace(',', '.').toDoubleOrNull()
        if (value == null || value <= 0.0) throw CurrencyParseException("Bad value '$raw'")
        return value
    }

    private fun valueDate(html: String): String? =
        DATE_TAG.find(html)?.value?.let { CONTENT_ATTR.find(it)?.groupValues?.get(1) }
}
