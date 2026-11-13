package com.radagva.widgetkit.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Streams the release APK straight into a PackageInstaller session and commits it. */
object UpdateInstaller {

    class CancelledException : IOException("Cancelled")

    /**
     * Blocking. [onProgress] receives bytes downloaded and the total (-1 when unknown).
     * On commit, the system shows its install confirmation (via [InstallResultReceiver]).
     */
    fun downloadAndInstall(
        context: Context,
        release: AppRelease,
        isCancelled: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true // GitHub redirects assets to its CDN
                setRequestProperty("User-Agent", "WidgetKit-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize

            connection.inputStream.use { input ->
                session.openWrite("base.apk", 0, total).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -2
                    onProgress(0, total)
                    while (true) {
                        if (isCancelled()) throw CancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        // Report at most once per percent (or every 64 KB when the size is unknown).
                        val percent = if (total > 0) (written * 100 / total).toInt() else (written / 65_536).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(written, total)
                        }
                    }
                    session.fsync(output)
                }
            }
            session.commit(statusReceiver(context, sessionId).intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
            connection?.disconnect()
        }
    }

    private fun statusReceiver(context: Context, sessionId: Int): PendingIntent {
        // Mutable: the system fills in the install status extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallResultReceiver::class.java),
            flags,
        )
    }
}
