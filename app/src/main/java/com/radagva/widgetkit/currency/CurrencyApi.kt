package com.radagva.widgetkit.currency

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface FetchResult {
    data class Success(val body: String) : FetchResult
    data class Failure(val error: FetchError) : FetchResult
}

/**
 * The official rate is scraped from bcv.org.ve; the parallel one comes from dolarapi. Either can
 * fail on its own: without BCV we keep dolarapi's oficial (which mirrors BCV), without dolarapi
 * we show just the BCV rate. Only when both fail is the fetch a failure.
 */
object CurrencyApi {

    private const val TIMEOUT_MS = 8_000
    private const val BCV_URL = "https://www.bcv.org.ve/"
    private const val OFICIAL = "oficial"

    /** The BCV page holds every currency; reuse it across the parallel per-currency fetches. */
    private const val BCV_CACHE_MS = 60_000L
    private var bcvPage: Pair<Long, Result<String>>? = null

    /** Blocking; call from a background thread. */
    fun fetch(context: Context, currency: Currency): FetchResult {
        if (!isOnline(context)) return FetchResult.Failure(FetchError.NO_CONNECTION)

        val dolarApi = fetchDolarApi(currency)
        val official = bcvRate(currency)

        val rates = when (dolarApi) {
            is DolarApiResult.Rates ->
                if (official == null) dolarApi.rates
                else listOf(official) + dolarApi.rates.filterNot { it.source.equals(OFICIAL, ignoreCase = true) }
            is DolarApiResult.Error -> listOfNotNull(official)
        }
        if (rates.isEmpty()) {
            return FetchResult.Failure((dolarApi as? DolarApiResult.Error)?.error ?: FetchError.NOT_AVAILABLE)
        }
        return FetchResult.Success(CurrencyParser.serialize(rates))
    }

    private sealed interface DolarApiResult {
        data class Rates(val rates: List<CurrencyRate>) : DolarApiResult
        data class Error(val error: FetchError) : DolarApiResult
    }

    private fun fetchDolarApi(currency: Currency): DolarApiResult = try {
        val rates = CurrencyParser.parse(get(currency.url, accept = "application/json"))
        if (rates.isEmpty()) DolarApiResult.Error(FetchError.NOT_AVAILABLE) else DolarApiResult.Rates(rates)
    } catch (_: HttpStatusException) {
        DolarApiResult.Error(FetchError.NOT_AVAILABLE)
    } catch (_: CurrencyParseException) {
        DolarApiResult.Error(FetchError.PARSE)
    } catch (_: IOException) {
        // UnknownHost, timeouts, connection resets, TLS failures behind captive portals...
        DolarApiResult.Error(FetchError.NO_CONNECTION)
    }

    /** The official rate from bcv.org.ve, or null if the page can't be fetched or parsed. */
    private fun bcvRate(currency: Currency): CurrencyRate? {
        val html = bcvPage().getOrNull() ?: return null
        val rate = try {
            BcvParser.parse(html, currency)
        } catch (_: CurrencyParseException) {
            return null
        }
        return CurrencyRate(
            source = OFICIAL,
            name = currency.name,
            buy = null,
            sell = null,
            average = rate.value,
            updatedAt = rate.valueDate ?: nowIso(),
        )
    }

    @Synchronized
    private fun bcvPage(): Result<String> {
        val now = System.currentTimeMillis()
        bcvPage?.let { (at, page) -> if (now - at < BCV_CACHE_MS) return page }
        // The site is slow at times; a failure is cached too so the second currency doesn't wait again.
        val page = runCatching { get(BCV_URL, accept = "text/html") }
        bcvPage = now to page
        return page
    }

    private class HttpStatusException(code: Int) : IOException("HTTP $code")

    private fun get(url: String, accept: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", accept)
        }
        try {
            if (connection.responseCode !in 200..299) throw HttpStatusException(connection.responseCode)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
