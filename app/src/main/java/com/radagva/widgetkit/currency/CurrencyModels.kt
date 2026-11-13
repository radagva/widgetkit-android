package com.radagva.widgetkit.currency

import androidx.annotation.StringRes
import com.radagva.widgetkit.R

enum class Currency(
    private val path: String,
    @StringRes val title: Int,
    @StringRes val calculatorTitle: Int,
    val symbol: String,
) {
    USD("dolares", R.string.currency_usd_title, R.string.calculator_title_usd, "$"),
    EUR("euros", R.string.currency_eur_title, R.string.calculator_title_eur, "€");

    val url: String get() = "https://ve.dolarapi.com/v1/$path"
}

/** One entry of the dolarapi response. compra/venta/promedio may be null. */
data class CurrencyRate(
    val source: String, // fuente
    val name: String, // nombre
    val buy: Double?, // compra
    val sell: Double?, // venta
    val average: Double?, // promedio
    val updatedAt: String, // fechaActualizacion (ISO-8601)
) {
    /** The value shown in widgets: promedio, falling back to venta, then compra. */
    val value: Double? get() = average ?: sell ?: buy
}

enum class FetchError {
    /** No network, DNS failure, timeout. Shown with the "no connection" icon. */
    NO_CONNECTION,

    /** Non-2xx response or an empty list. Shown with the question icon. */
    NOT_AVAILABLE,

    /** Body isn't the expected JSON shape. Shown with the hammer icon. */
    PARSE,
}

sealed interface CurrencyState {
    /** No data yet (first fetch in flight). */
    data object Loading : CurrencyState

    data class Loaded(val rates: List<CurrencyRate>, val refreshing: Boolean) : CurrencyState

    data class Failed(val error: FetchError, val refreshing: Boolean) : CurrencyState
}
