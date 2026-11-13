package com.radagva.widgetkit.update

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.radagva.widgetkit.R
import com.radagva.widgetkit.theme.WidgetSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Checks GitHub for a newer release when the app is opened and walks the user through the
 * update in one bottom sheet: offer → (install permission) → download progress → system
 * install prompt, or a failure state with retry. Create it as a property of the activity.
 */
class AppUpdater(private val activity: AppCompatActivity) {

    private val executor = Executors.newSingleThreadExecutor()

    private var sheet: BottomSheetDialog? = null
    private lateinit var title: TextView
    private lateinit var version: TextView
    private lateinit var detail: TextView
    private lateinit var progressGroup: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var dismissButton: MaterialButton
    private lateinit var acceptButton: MaterialButton

    private var release: AppRelease? = null
    private var installed: String = ""
    private var cancelled = AtomicBoolean(false)

    // Returning from the "Install unknown apps" settings screen.
    private val unknownSourcesSettings =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val pending = release ?: return@registerForActivityResult
            if (canInstallPackages()) download(pending)
        }

    /** Call from onCreate when savedInstanceState == null (i.e. once per app open). */
    fun checkOnLaunch() {
        val current = installedVersion() ?: return
        executor.execute {
            val latest = UpdateChecker.latestRelease() ?: return@execute
            if (!UpdateChecker.isNewer(latest.version, current)) return@execute
            activity.runOnUiThread {
                if (!isAlive()) return@runOnUiThread
                release = latest
                installed = current
                showOffer()
            }
        }
    }

    // ───────────── States ─────────────

    private fun showOffer() {
        val r = release ?: return
        ensureSheet()
        title.setText(R.string.update_title)
        version.text = activity.getString(R.string.update_version, r.version)
        detail.text = listOfNotNull(
            activity.getString(R.string.update_installed, installed),
            r.apkSize.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(activity, it) },
        ).joinToString(" · ")
        progressGroup.visibility = View.GONE
        setButtons(
            dismiss = R.string.update_later to { sheet?.dismiss() },
            accept = R.string.update_accept to { start(r) },
        )
        setCancelable(true)
    }

    private fun showPermission() {
        ensureSheet()
        title.setText(R.string.update_permission_title)
        detail.setText(R.string.update_permission_message)
        progressGroup.visibility = View.GONE
        setButtons(
            dismiss = R.string.update_later to { sheet?.dismiss() },
            accept = R.string.update_permission_open to {
                unknownSourcesSettings.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                )
            },
        )
        setCancelable(true)
    }

    private fun showDownloading() {
        ensureSheet()
        title.setText(R.string.update_downloading_title)
        detail.text = activity.getString(R.string.update_installed, installed)
        progressGroup.visibility = View.VISIBLE
        progress.isIndeterminate = true
        progressText.setText(R.string.update_connecting)
        setButtons(
            dismiss = android.R.string.cancel to { cancelled.set(true) },
            accept = null,
        )
        setCancelable(false) // only the Cancel button stops the download
    }

    private fun updateProgress(downloaded: Long, total: Long) {
        if (sheet == null) return
        val done = Formatter.formatShortFileSize(activity, downloaded)
        if (total > 0) {
            val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
            progress.isIndeterminate = false
            progress.setProgressCompat(percent, true)
            progressText.text = activity.getString(
                R.string.update_progress, percent, done, Formatter.formatShortFileSize(activity, total),
            )
        } else {
            progress.isIndeterminate = true
            progressText.text = done
        }
    }

    private fun showInstalling() {
        title.setText(R.string.update_installing_title)
        progress.isIndeterminate = false
        progress.setProgressCompat(100, true)
        progressText.setText(R.string.update_installing)
        setButtons(dismiss = null, accept = null)
        // The system install confirmation takes over from here (InstallResultReceiver).
        acceptButton.postDelayed({ sheet?.dismiss() }, INSTALL_HANDOFF_MS)
    }

    private fun showFailed() {
        val r = release ?: return
        ensureSheet()
        title.setText(R.string.update_failed_title)
        detail.setText(R.string.update_failed)
        progressGroup.visibility = View.GONE
        setButtons(
            dismiss = R.string.update_close to { sheet?.dismiss() },
            accept = R.string.update_retry to { start(r) },
        )
        setCancelable(true)
    }

    // ───────────── Flow ─────────────

    private fun start(target: AppRelease) {
        if (canInstallPackages()) download(target) else showPermission() // Android 8+: allow once
    }

    private fun download(target: AppRelease) {
        cancelled = AtomicBoolean(false)
        val stop = cancelled
        showDownloading()
        executor.execute {
            val result = runCatching {
                UpdateInstaller.downloadAndInstall(activity.applicationContext, target, stop::get) { downloaded, total ->
                    activity.runOnUiThread { if (!stop.get()) updateProgress(downloaded, total) }
                }
            }
            activity.runOnUiThread {
                if (!isAlive()) return@runOnUiThread
                val error = result.exceptionOrNull()
                when {
                    error == null -> showInstalling()
                    error is UpdateInstaller.CancelledException -> showOffer()
                    else -> showFailed()
                }
            }
        }
    }

    // ───────────── Sheet ─────────────

    private fun ensureSheet() {
        if (sheet != null) return
        val dialog = BottomSheetDialog(activity)
        val content = activity.layoutInflater.inflate(R.layout.sheet_update, null)
        dialog.setContentView(content)
        title = content.findViewById(R.id.update_title)
        version = content.findViewById(R.id.update_version)
        detail = content.findViewById(R.id.update_detail)
        progressGroup = content.findViewById(R.id.update_progress_group)
        progress = content.findViewById(R.id.update_progress)
        progressText = content.findViewById(R.id.update_progress_text)
        dismissButton = content.findViewById(R.id.update_dismiss)
        acceptButton = content.findViewById(R.id.update_accept)

        // Raised button and progress bar use the global color scheme.
        val palette = WidgetSettings.globalScheme(activity).palette
        acceptButton.backgroundTintList = ColorStateList.valueOf(palette.accent)
        acceptButton.setTextColor(palette.onAccent)
        acceptButton.rippleColor = ColorStateList.valueOf(palette.onAccent and 0x33FFFFFF)
        progress.setIndicatorColor(palette.accent)
        progress.trackColor = activity.getColor(R.color.app_separator)

        dialog.setOnShowListener {
            // Let our rounded background show (the default container is a plain rectangle).
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.setOnDismissListener {
            cancelled.set(true)
            sheet = null
        }
        sheet = dialog
        dialog.show()
    }

    private fun setButtons(dismiss: Pair<Int, () -> Unit>?, accept: Pair<Int, () -> Unit>?) {
        dismissButton.visibility = if (dismiss == null) View.GONE else View.VISIBLE
        dismiss?.let { (label, action) ->
            dismissButton.setText(label)
            dismissButton.setOnClickListener { action() }
        }
        acceptButton.visibility = if (accept == null) View.GONE else View.VISIBLE
        accept?.let { (label, action) ->
            acceptButton.setText(label)
            acceptButton.setOnClickListener { action() }
        }
    }

    private fun setCancelable(cancelable: Boolean) {
        sheet?.setCancelable(cancelable)
        sheet?.behavior?.isDraggable = cancelable
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun installedVersion(): String? =
        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull()

    private fun isAlive() = !activity.isFinishing && !activity.isDestroyed

    private companion object {
        const val INSTALL_HANDOFF_MS = 600L
    }
}
