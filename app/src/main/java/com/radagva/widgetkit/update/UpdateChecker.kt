package com.radagva.widgetkit.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release that ships an APK. */
data class AppRelease(
    val version: String, // tag without a leading "v"
    val apkUrl: String,
    val apkSize: Long,
)

object UpdateChecker {

    /** Repository the release workflow publishes to. */
    private const val REPO = "radagva/widgetkit-android"
    private const val TIMEOUT_MS = 8_000

    /** Latest published release with an APK asset, or null on any failure. Blocking. */
    fun latestRelease(): AppRelease? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WidgetKit-Android") // GitHub requires a User-Agent
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null

            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) } ?: return null
            AppRelease(
                version = json.getString("tag_name").removePrefix("v"),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size", -1),
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** True if [candidate] is a higher x.y.z version than [installed]. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun parse(version: String): List<Int>? =
        version.removePrefix("v").split('.').map { it.toIntOrNull() ?: return null }
}
