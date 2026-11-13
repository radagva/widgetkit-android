package com.radagva.widgetkit.currency

import android.content.Context
import android.content.SharedPreferences

/**
 * Last known state per currency, shared by every currency widget (and the app preview).
 * The raw JSON of the last successful response is kept so widgets render without refetching.
 */
object CurrencyStore {

    const val PREFS = "currency_state"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context, currency: Currency): CurrencyState {
        val prefs = prefs(context)
        val refreshing = prefs.getBoolean(refreshingKey(currency), false)
        prefs.getString(errorKey(currency), null)
            ?.let { runCatching { FetchError.valueOf(it) }.getOrNull() }
            ?.let { return CurrencyState.Failed(it, refreshing) }

        val body = prefs.getString(bodyKey(currency), null) ?: return CurrencyState.Loading
        return try {
            CurrencyState.Loaded(CurrencyParser.parse(body), refreshing)
        } catch (_: CurrencyParseException) {
            CurrencyState.Failed(FetchError.PARSE, refreshing)
        }
    }

    /** Rates from the last successful response, regardless of later errors. */
    fun lastRates(context: Context, currency: Currency): List<CurrencyRate> {
        val body = prefs(context).getString(bodyKey(currency), null) ?: return emptyList()
        return try {
            CurrencyParser.parse(body)
        } catch (_: CurrencyParseException) {
            emptyList()
        }
    }

    fun setRefreshing(context: Context, currencies: Collection<Currency>) {
        val editor = prefs(context).edit()
        currencies.forEach { editor.putBoolean(refreshingKey(it), true) }
        editor.commit()
    }

    fun save(context: Context, currency: Currency, result: FetchResult) {
        val editor = prefs(context).edit().putBoolean(refreshingKey(currency), false)
        when (result) {
            is FetchResult.Success -> {
                editor.putString(bodyKey(currency), result.body).remove(errorKey(currency))
                recordHistory(context, editor, currency, result.body)
            }
            is FetchResult.Failure -> editor.putString(errorKey(currency), result.error.name)
        }
        editor.commit()
    }

    /**
     * Difference between a rate's current value and the value it had before it last changed,
     * or null if it hasn't changed since we started tracking it.
     */
    fun change(context: Context, currency: Currency, rate: CurrencyRate): Double? {
        val prefs = prefs(context)
        val current = rate.value ?: return null
        val previous = prefs.getString(previousKey(currency, rate.source), null)?.toDoubleOrNull() ?: return null
        return current - previous
    }

    /**
     * Keeps, per rate source, the last value and the value before it. Only an actual change
     * moves current -> previous, so refreshes returning the same data keep the last difference.
     */
    private fun recordHistory(context: Context, editor: SharedPreferences.Editor, currency: Currency, body: String) {
        val prefs = prefs(context)
        val rates = try {
            CurrencyParser.parse(body)
        } catch (_: CurrencyParseException) {
            return
        }
        rates.forEach { rate ->
            val value = rate.value ?: return@forEach
            val stored = prefs.getString(currentKey(currency, rate.source), null)?.toDoubleOrNull()
            if (stored == null || kotlin.math.abs(stored - value) > EPSILON) {
                stored?.let { editor.putString(previousKey(currency, rate.source), it.toString()) }
                editor.putString(currentKey(currency, rate.source), value.toString())
            }
        }
    }

    private const val EPSILON = 1e-9

    private fun currentKey(c: Currency, source: String) = "${c.name}_${source}_current"
    private fun previousKey(c: Currency, source: String) = "${c.name}_${source}_previous"
    private fun bodyKey(c: Currency) = "${c.name}_body"
    private fun errorKey(c: Currency) = "${c.name}_error"
    private fun refreshingKey(c: Currency) = "${c.name}_refreshing"
}
