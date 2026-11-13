package com.radagva.widgetkit.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.radagva.widgetkit.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub for a newer release when the app is opened, asks the user, and
 * downloads + installs it on accept. Create it as a property of the activity.
 */
class AppUpdater(private val activity: AppCompatActivity) {

    private val executor = Executors.newSingleThreadExecutor()
    private var pendingRelease: AppRelease? = null

    // Returning from the "Install unknown apps" settings screen.
    private val unknownSourcesSettings =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val release = pendingRelease ?: return@registerForActivityResult
            if (canInstallPackages()) download(release)
        }

    /** Call from onCreate when savedInstanceState == null (i.e. once per app open). */
    fun checkOnLaunch() {
        val installed = installedVersion() ?: return
        executor.execute {
            val release = UpdateChecker.latestRelease() ?: return@execute
            if (!UpdateChecker.isNewer(release.version, installed)) return@execute
            activity.runOnUiThread { if (isAlive()) prompt(release, installed) }
        }
    }

    private fun prompt(release: AppRelease, installed: String) {
        val size = release.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(activity, it) }
        val message = activity.getString(R.string.update_message, release.version, installed) +
            (size?.let { "\n\n" + activity.getString(R.string.update_size, it) } ?: "")
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_accept) { _, _ -> start(release) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun start(release: AppRelease) {
        if (canInstallPackages()) {
            download(release)
            return
        }
        // Android 8+: the user must allow this app to install packages once.
        pendingRelease = release
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_permission_title)
            .setMessage(R.string.update_permission_message)
            .setPositiveButton(R.string.update_permission_open) { _, _ ->
                unknownSourcesSettings.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                )
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun download(release: AppRelease) {
        pendingRelease = null
        val cancelled = AtomicBoolean(false)
        val label = TextView(activity).apply {
            text = activity.getString(R.string.update_downloading, release.version)
            setTextColor(activity.getColor(R.color.app_label))
            textSize = 16f
        }
        val progress = LinearProgressIndicator(activity).apply {
            isIndeterminate = true
            max = 100
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = pad / 2 })
        }
        val dialog: AlertDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_title)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .show()

        executor.execute {
            val result = runCatching {
                UpdateInstaller.downloadAndInstall(activity.applicationContext, release, cancelled::get) { percent ->
                    activity.runOnUiThread {
                        if (percent < 0) {
                            progress.isIndeterminate = true
                        } else {
                            progress.isIndeterminate = false
                            progress.setProgressCompat(percent, true)
                        }
                    }
                }
            }
            activity.runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                val error = result.exceptionOrNull()
                if (error != null && error !is UpdateInstaller.CancelledException && isAlive()) {
                    Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
                }
                // On success the system install confirmation appears (InstallResultReceiver).
            }
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun installedVersion(): String? =
        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull()

    private fun isAlive() = !activity.isFinishing && !activity.isDestroyed
}
