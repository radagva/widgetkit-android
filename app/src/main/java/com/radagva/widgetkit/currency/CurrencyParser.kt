package com.radagva.widgetkit.currency

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class CurrencyParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object CurrencyParser {

    /** Strictly parses the dolarapi array; any shape mismatch throws [CurrencyParseException]. */
    fun parse(body: String): List<CurrencyRate> = try {
        val array = JSONArray(body)
        List(array.length()) { i -> parseRate(array.getJSONObject(i)) }
    } catch (e: JSONException) {
        throw CurrencyParseException("Invalid JSON", e)
    }

    private fun parseRate(o: JSONObject): CurrencyRate {
        val rate = CurrencyRate(
            source = o.getString("fuente"),
            name = o.optString("nombre"),
            buy = o.nullableDouble("compra"),
            sell = o.nullableDouble("venta"),
            average = o.nullableDouble("promedio"),
            updatedAt = o.getString("fechaActualizacion"),
        )
        if (rate.average == null && rate.sell == null && rate.buy == null) {
            throw CurrencyParseException("Rate '${rate.source}' has no values")
        }
        return rate
    }

    private fun JSONObject.nullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = get(key)
        return (value as? Number)?.toDouble() ?: throw CurrencyParseException("'$key' is not a number")
    }
}
