package com.radagva.widgetkit.currency

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface FetchResult {
    data class Success(val body: String) : FetchResult
    data class Failure(val error: FetchError) : FetchResult
}

object CurrencyApi {

    private const val TIMEOUT_MS = 8_000

    /** Blocking; call from a background thread. */
    fun fetch(context: Context, currency: Currency): FetchResult {
        if (!isOnline(context)) return FetchResult.Failure(FetchError.NO_CONNECTION)

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(currency.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) {
                return FetchResult.Failure(FetchError.NOT_AVAILABLE)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val rates = CurrencyParser.parse(body)
            if (rates.isEmpty()) FetchResult.Failure(FetchError.NOT_AVAILABLE) else FetchResult.Success(body)
        } catch (_: CurrencyParseException) {
            FetchResult.Failure(FetchError.PARSE)
        } catch (_: IOException) {
            // UnknownHost, timeouts, connection resets, TLS failures behind captive portals...
            FetchResult.Failure(FetchError.NO_CONNECTION)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
