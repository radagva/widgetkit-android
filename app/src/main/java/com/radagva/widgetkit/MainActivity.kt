package com.radagva.widgetkit

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.radagva.widgetkit.calendar.CalendarEventsRepository
import com.radagva.widgetkit.colorpicker.ColorPickerTileService
import com.radagva.widgetkit.calendar.CalendarWidgetProvider
import com.radagva.widgetkit.calendar.CalendarWidgetRenderer
import com.radagva.widgetkit.currency.Currency
import com.radagva.widgetkit.currency.CurrencyStore
import com.radagva.widgetkit.currency.CurrencyWidgetOptions
import com.radagva.widgetkit.currency.CurrencyWidgets
import com.radagva.widgetkit.currency.RateSources
import com.radagva.widgetkit.theme.ColorScheme
import com.radagva.widgetkit.theme.ThemeFamily
import com.radagva.widgetkit.theme.WidgetKind
import com.radagva.widgetkit.theme.WidgetSettings
import com.radagva.widgetkit.update.AppUpdater
import com.radagva.widgetkit.uuid.UuidStore
import com.radagva.widgetkit.uuid.UuidVersion
import com.radagva.widgetkit.uuid.UuidWidgetProvider
import com.radagva.widgetkit.uuid.UuidWidgets
import com.radagva.widgetkit.uuid.description
import com.radagva.widgetkit.update.UpdateChecker

/**
 * Settings screen: global options first, then one accordion panel per widget (only one open
 * at a time) with its live preview and options, then credits and the app version.
 */
class MainActivity : AppCompatActivity() {

    private val updater = AppUpdater(this)

    private lateinit var content: LinearLayout
    private lateinit var globalList: LinearLayout
    private lateinit var accordion: LinearLayout

    /** The open accordion panel, or null when all are collapsed. */
    private var expanded: WidgetKind? = WidgetKind.CALENDAR
    private val panels = mutableMapOf<WidgetKind, Panel>()

    private class Panel(val subtitle: TextView, val chevron: ImageView, val body: LinearLayout)

    private val requestCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        CalendarWidgetProvider.updateAll(this)
        rebuildBody(WidgetKind.CALENDAR)
    }

    // A fetch stored new rates: redraw the open currency preview (called on the main thread).
    private val currencyListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        expanded?.takeIf { it.isCurrency }?.let(::renderPreview)
    }

    // The UUID changed (here or from the widget): refresh the section and the open preview.
    private val uuidListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshUuid()
        if (expanded == WidgetKind.UUID) renderPreview(WidgetKind.UUID)
    }

    private val WidgetKind.isCurrency get() = this == WidgetKind.USD || this == WidgetKind.EUR || this == WidgetKind.BOTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Only on a fresh open (not on rotation / recreation).
        if (savedInstanceState == null) updater.checkOnLaunch()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        content = findViewById(R.id.content)
        globalList = findViewById(R.id.global_list)
        accordion = findViewById(R.id.accordion)
        savedInstanceState?.let { state ->
            expanded = state.getString(KEY_EXPANDED)?.let { runCatching { WidgetKind.valueOf(it) }.getOrNull() }
        }

        buildGlobal()
        buildTools()
        buildUuid()
        buildAccordion()
        buildCredits()
        showAppVersion()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_EXPANDED, expanded?.name)
    }

    override fun onStart() {
        super.onStart()
        CurrencyStore.prefs(this).registerOnSharedPreferenceChangeListener(currencyListener)
        UuidStore.prefs(this).registerOnSharedPreferenceChangeListener(uuidListener)
        refreshUuid()
        // Fresh rates every time the app is opened (also updates home-screen widgets).
        CurrencyWidgets.refresh(this, Currency.entries)
    }

    override fun onStop() {
        CurrencyStore.prefs(this).unregisterOnSharedPreferenceChangeListener(currencyListener)
        UuidStore.prefs(this).unregisterOnSharedPreferenceChangeListener(uuidListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Calendar permission may have changed in system settings while we were away.
        expanded?.let(::rebuildBody)
    }

    // ───────────── Global ─────────────

    private fun buildGlobal() {
        val global = WidgetSettings.globalScheme(this)
        globalList.removeAllViews()
        globalList.addView(valueRow(getString(R.string.settings_color_scheme), global.displayName, global) {
            pick(
                title = R.string.settings_color_scheme,
                options = ColorScheme.entries.map { it to it.displayName },
                selected = global,
                swatch = { it },
            ) { scheme ->
                WidgetSettings.setGlobalScheme(this, scheme)
                updateAllWidgets()
                buildGlobal()
                refreshUuid()
                WidgetKind.entries.forEach(::refreshSubtitle)
                expanded?.let(::rebuildBody)
            }
        })
    }

    // ───────────── Tools ─────────────

    private fun buildTools() {
        val list = findViewById<LinearLayout>(R.id.tools_list)
        list.removeAllViews()
        val row = listRow { addColorPickerTile() }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_tile_color_picker)
            imageTintList = ColorStateList.valueOf(getColor(R.color.app_tint))
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) }
        })
        row.addView(label(getString(R.string.settings_color_picker_add), color = R.color.app_tint))
        list.addView(row)
    }

    /** Android 13+ can add the tile with one tap; older versions need the Quick Settings editor. */
    private fun addColorPickerTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, ColorPickerTileService::class.java),
                getString(R.string.color_picker_tile_label),
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile_color_picker),
                mainExecutor,
            ) { }
        } else {
            Toast.makeText(this, R.string.settings_color_picker_manual, Toast.LENGTH_LONG).show()
        }
    }

    // ───────────── UUID generator ─────────────

    private lateinit var uuidVersionValue: TextView
    private lateinit var uuidValue: TextView
    private lateinit var uuidCopy: MaterialButton

    private fun buildUuid() {
        val list = findViewById<LinearLayout>(R.id.uuid_list)
        list.removeAllViews()

        // Version dropdown.
        val versionRow = listRow {}
        versionRow.addView(label(getString(R.string.uuid_version)).apply { maxLines = 1 })
        uuidVersionValue = label("", color = R.color.app_secondary_label).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) }
        }
        versionRow.addView(uuidVersionValue)
        versionRow.addView(chevron().apply { rotation = 90f })
        versionRow.setOnClickListener { showUuidVersions(versionRow) }
        list.addView(versionRow)
        list.addView(separator())

        // Current UUID.
        uuidValue = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 16f
            setTextColor(getColor(R.color.app_label))
            gravity = Gravity.CENTER
            setTextIsSelectable(true)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
        list.addView(uuidValue)

        // Generate (flat) + Copy (raised, global scheme).
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        val generate = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            setText(R.string.uuid_generate)
            styleSheetButton(this)
            setTextColor(getColor(R.color.app_label))
            rippleColor = ColorStateList.valueOf(getColor(R.color.app_separator))
            setOnClickListener {
                UuidStore.generate(this@MainActivity)
                UuidWidgets.updateAll(this@MainActivity)
            }
        }
        uuidCopy = MaterialButton(this).apply {
            setText(R.string.uuid_copy)
            styleSheetButton(this)
            val palette = WidgetSettings.globalScheme(this@MainActivity).palette
            backgroundTintList = ColorStateList.valueOf(palette.accent)
            setTextColor(palette.onAccent)
            setOnClickListener { copyUuid() }
        }
        buttons.addView(generate, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        buttons.addView(uuidCopy, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        list.addView(buttons)
        refreshUuid()
    }

    private fun styleSheetButton(button: MaterialButton) {
        button.isAllCaps = false
        button.letterSpacing = 0f
        button.textSize = 16f
        button.insetTop = 0
        button.insetBottom = 0
        button.cornerRadius = dp(12)
    }

    private fun refreshUuid() {
        if (!::uuidValue.isInitialized) return
        val state = UuidStore.state(this)
        uuidVersionValue.text = versionLabel(state.version)
        uuidValue.text = state.value
        // The raised button follows the global scheme.
        val palette = WidgetSettings.globalScheme(this).palette
        uuidCopy.backgroundTintList = ColorStateList.valueOf(palette.accent)
        uuidCopy.setTextColor(palette.onAccent)
    }

    private fun versionLabel(version: UuidVersion) =
        getString(R.string.uuid_version_label, version.number, getString(version.description))

    private fun showUuidVersions(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor, Gravity.END)
        UuidVersion.entries.forEachIndexed { index, version ->
            popup.menu.add(0, index, index, versionLabel(version)).apply {
                isCheckable = true
                isChecked = version == UuidStore.state(this@MainActivity).version
            }
        }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            val version = UuidVersion.entries[item.itemId]
            if (version != UuidStore.state(this).version) {
                UuidStore.generate(this, version)
                UuidWidgets.updateAll(this)
            }
            true
        }
        popup.show()
    }

    private fun copyUuid() {
        UuidWidgets.copy(this, UuidStore.state(this).value)
        uuidCopy.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY,
        )
        uuidCopy.setText(R.string.uuid_copied)
        uuidCopy.removeCallbacks(restoreCopyLabel)
        uuidCopy.postDelayed(restoreCopyLabel, 1_000)
    }

    private val restoreCopyLabel = Runnable { uuidCopy.setText(R.string.uuid_copy) }

    // ───────────── Accordion ─────────────

    private fun buildAccordion() {
        accordion.removeAllViews()
        panels.clear()
        WidgetKind.entries.forEachIndexed { index, kind ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.app_card)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { if (index > 0) topMargin = dp(10) }
            }
            val header = listRow { toggle(kind) }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            texts.addView(label(getString(kind.label), size = 17f, bold = true))
            val subtitle = label("", size = 13f, color = R.color.app_secondary_label)
            texts.addView(subtitle)
            header.addView(texts)
            val chevron = chevron()
            header.addView(chevron)

            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            card.addView(header)
            card.addView(body)
            accordion.addView(card)
            panels[kind] = Panel(subtitle, chevron, body)
            refreshSubtitle(kind)
        }
        expanded?.let { open(it, animate = false) }
    }

    /** Opens [kind] (closing the open one), or closes it if it's already open. */
    private fun toggle(kind: WidgetKind) {
        TransitionManager.beginDelayedTransition(content, AutoTransition().setDuration(200))
        val previous = expanded
        previous?.let(::close)
        expanded = if (previous == kind) null else kind
        expanded?.let { open(it, animate = true) }
    }

    private fun open(kind: WidgetKind, animate: Boolean) {
        val panel = panels.getValue(kind)
        rebuildBody(kind)
        panel.body.visibility = View.VISIBLE
        if (animate) panel.chevron.animate().rotation(90f).setDuration(200).start() else panel.chevron.rotation = 90f
    }

    private fun close(kind: WidgetKind) {
        val panel = panels.getValue(kind)
        panel.body.visibility = View.GONE
        panel.body.removeAllViews()
        panel.chevron.animate().rotation(0f).setDuration(200).start()
    }

    private fun refreshSubtitle(kind: WidgetKind) {
        panels[kind]?.subtitle?.text = schemeSummary(kind)
    }

    /** "Global · Dark 2026" or the widget's own scheme name. */
    private fun schemeSummary(kind: WidgetKind): String =
        WidgetSettings.override(this, kind)?.displayName
            ?: getString(R.string.scheme_follow_global, WidgetSettings.globalScheme(this).displayName)

    /** (Re)builds the open panel's content: preview + options. No-op for closed panels. */
    private fun rebuildBody(kind: WidgetKind) {
        if (expanded != kind) return
        val body = panels[kind]?.body ?: return
        body.removeAllViews()

        body.addView(previewFrame(kind))

        body.addView(separator(inset = false))
        val effective = WidgetSettings.theme(this, kind).scheme
        body.addView(valueRow(getString(R.string.settings_color_scheme), schemeSummary(kind), effective) {
            val global = WidgetSettings.globalScheme(this)
            pick(
                title = R.string.settings_color_scheme,
                options = listOf<Pair<ColorScheme?, String>>(null to getString(R.string.scheme_use_global, global.displayName)) +
                    ColorScheme.entries.map { it to it.displayName },
                selected = WidgetSettings.override(this, kind),
                swatch = { it ?: global },
            ) { scheme ->
                WidgetSettings.setOverride(this, kind, scheme)
                updateWidget(kind)
                refreshSubtitle(kind)
                rebuildBody(kind)
            }
        })

        if (kind == WidgetKind.CALENDAR) {
            body.addView(separator())
            body.addView(calendarAccessRow())
        } else if (kind.isCurrency) {
            val options = CurrencyWidgetOptions.load(this, kind)
            body.addView(separator())
            body.addView(valueRow(getString(R.string.settings_sources), getString(options.sources.label)) {
                pick(
                    title = R.string.settings_sources,
                    options = RateSources.entries.map { it to getString(it.label) },
                    selected = options.sources,
                ) { sources -> saveOptions(kind, options.copy(sources = sources)) }
            })
            body.addView(separator())
            body.addView(switchRow(
                title = getString(R.string.settings_show_change),
                subtitle = getString(R.string.settings_show_change_hint),
                checked = options.showChange,
            ) { checked -> saveOptions(kind, CurrencyWidgetOptions.load(this, kind).copy(showChange = checked)) })
        }

        body.addView(separator())
        body.addView(listRow { pinWidget(kind) }.apply {
            addView(label(getString(R.string.settings_add_to_home), color = R.color.app_tint))
        })
    }

    private fun saveOptions(kind: WidgetKind, options: CurrencyWidgetOptions) {
        CurrencyWidgetOptions.save(this, kind, options)
        updateWidget(kind)
        rebuildBody(kind)
    }

    // ───────────── Previews ─────────────

    private val previewContainers = mutableMapOf<WidgetKind, FrameLayout>()

    private fun previewFrame(kind: WidgetKind): View {
        val backdrop = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.app_preview_backdrop)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(dp(12), 0, dp(12), dp(12))
            }
        }
        val (width, height) = when (kind) {
            WidgetKind.CALENDAR -> MATCH to dp(160)
            WidgetKind.USD, WidgetKind.EUR -> dp(160) to dp(150)
            WidgetKind.BOTH -> MATCH to dp(150)
            WidgetKind.UUID -> MATCH to dp(96)
        }
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER_HORIZONTAL)
        }
        backdrop.addView(container)
        previewContainers[kind] = container
        renderPreview(kind)
        return backdrop
    }

    private fun renderPreview(kind: WidgetKind) {
        if (expanded != kind) return
        val container = previewContainers[kind] ?: return
        val views = when (kind) {
            WidgetKind.CALENDAR -> {
                val now = System.currentTimeMillis()
                CalendarWidgetRenderer.render(
                    context = this,
                    appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                    now = now,
                    monthOffset = 0,
                    hasPermission = CalendarEventsRepository.hasPermission(this),
                    events = CalendarEventsRepository.upcomingToday(this, now),
                    theme = WidgetSettings.theme(this, kind),
                )
            }
            WidgetKind.UUID -> UuidWidgets.render(this)
            else -> CurrencyWidgets.render(this, CurrencyWidgets.spec(kind))
        }
        show(container, views)
    }

    private fun show(container: FrameLayout, views: RemoteViews) {
        container.removeAllViews()
        // Inflate with the application context: the activity's AppCompat inflater would swap in
        // AppCompatImageView, whose setImageResource isn't allowed in RemoteViews (crashes the
        // preview when a widget shows an error icon).
        container.addView(views.apply(applicationContext, container))
    }

    // ───────────── Widgets on the home screen ─────────────

    private fun updateWidget(kind: WidgetKind) {
        when {
            kind == WidgetKind.CALENDAR -> CalendarWidgetProvider.updateAll(this)
            kind == WidgetKind.UUID -> UuidWidgets.updateAll(this)
            else -> CurrencyWidgets.updateAll(this)
        }
    }

    private fun updateAllWidgets() {
        CalendarWidgetProvider.updateAll(this)
        CurrencyWidgets.updateAll(this)
        UuidWidgets.updateAll(this)
    }

    private fun providerOf(kind: WidgetKind): Class<out AppWidgetProvider> =
        when (kind) {
            WidgetKind.CALENDAR -> CalendarWidgetProvider::class.java
            WidgetKind.UUID -> UuidWidgetProvider::class.java
            else -> CurrencyWidgets.spec(kind).provider
        }

    private fun pinWidget(kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(this)
        val pinned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(this, providerOf(kind)), null, null)
        if (!pinned) {
            Toast.makeText(this, R.string.settings_add_widget_manual, Toast.LENGTH_LONG).show()
        }
    }

    // ───────────── Calendar access ─────────────

    private fun calendarAccessRow(): View {
        val granted = CalendarEventsRepository.hasPermission(this)
        val asked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ASKED, false)
        // After a permanent denial the system won't show the dialog again; send to Settings.
        val blocked = !granted && asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)

        val row = listRow(clickable = !granted) {
            if (blocked) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            } else {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
                requestCalendar.launch(Manifest.permission.READ_CALENDAR)
            }
        }
        row.addView(label(
            getString(if (granted) R.string.settings_access_granted else R.string.settings_access_missing),
        ).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
        if (!granted) {
            row.addView(label(
                getString(if (blocked) R.string.settings_access_open_settings else R.string.settings_access_allow),
                color = R.color.app_tint,
            ).apply { setPadding(dp(12), 0, 0, 0) })
        }
        return row
    }

    // ───────────── Credits & version ─────────────

    private fun buildCredits() {
        val list = findViewById<LinearLayout>(R.id.credits_list)
        list.removeAllViews()
        ThemeFamily.entries.forEachIndexed { index, family ->
            if (index > 0) list.addView(separator())
            val row = listRow { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(family.url))) }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            }
            texts.addView(label(family.displayName))
            texts.addView(label(family.shortUrl, size = 13f, color = R.color.app_secondary_label))
            row.addView(texts)
            row.addView(chevron())
            list.addView(row)
        }
    }

    private fun showAppVersion() {
        val info = packageManager.getPackageInfo(packageName, 0)
        val version = info.versionName.orEmpty()
        findViewById<TextView>(R.id.app_version).text =
            getString(R.string.settings_version, version, PackageInfoCompat.getLongVersionCode(info))
        findViewById<View>(R.id.share_version).setOnClickListener { shareDownloadLink(version) }
    }

    /** Shares the GitHub download link of the installed version. */
    private fun shareDownloadLink(version: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "WidgetKit $version")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.settings_share_text, version, UpdateChecker.downloadUrl(version)))
        startActivity(Intent.createChooser(send, getString(R.string.settings_share_version)))
    }

    // ───────────── Picker dialog ─────────────

    /** iOS-style single-choice dialog: [swatch]  Label  ✓ */
    private fun <T> pick(
        title: Int,
        options: List<Pair<T, String>>,
        selected: T,
        swatch: ((T) -> ColorScheme)? = null,
        onPick: (T) -> Unit,
    ) {
        lateinit var dialog: AlertDialog
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        options.forEach { (value, text) ->
            val row = listRow {
                dialog.dismiss()
                if (value != selected) onPick(value)
            }
            swatch?.let { row.addView(schemeSwatch(it(value))) }
            row.addView(label(text).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (swatch != null) marginStart = dp(12) }
            })
            row.addView(checkMark().apply { visibility = if (value == selected) View.VISIBLE else View.INVISIBLE })
            list.addView(row)
        }
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ───────────── Row building blocks ─────────────

    /** "Title            value  ›", optionally with a scheme swatch before the value. */
    private fun valueRow(title: String, value: String, swatch: ColorScheme? = null, onClick: () -> Unit): View {
        val row = listRow(onClick = onClick)
        // Title never wraps; a long value is ellipsized instead.
        row.addView(label(title).apply { maxLines = 1 })
        row.addView(label(value, color = R.color.app_secondary_label).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12) }
        })
        swatch?.let { row.addView(schemeSwatch(it, small = true).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8) }) }
        row.addView(chevron())
        return row
    }

    private fun switchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = listRow(clickable = false) {}
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        texts.addView(label(title))
        texts.addView(label(subtitle, size = 13f, color = R.color.app_secondary_label))
        row.addView(texts)
        row.addView(SwitchMaterial(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        })
        return row
    }

    private fun listRow(clickable: Boolean = true, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        if (clickable) {
            setOnClickListener { onClick() }
            val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            foreground = attrs.getDrawable(0)
            attrs.recycle()
        }
    }

    private fun label(text: String, size: Float = 16f, color: Int = R.color.app_label, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(getColor(color))
            if (bold) paint.isFakeBoldText = true
        }

    private fun chevron() = ImageView(this).apply {
        setImageResource(R.drawable.widget_chevron_right)
        imageTintList = ColorStateList.valueOf(getColor(R.color.app_secondary_label))
        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(6) }
    }

    private fun checkMark() = TextView(this).apply {
        text = "✓"
        textSize = 17f
        setTextColor(getColor(R.color.app_tint))
    }

    private fun separator(inset: Boolean = true) = View(this).apply {
        setBackgroundColor(getColor(R.color.app_separator))
        layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { if (inset) marginStart = dp(16) }
    }

    /** A tiny widget-like chip: scheme background with accent, label and secondary dots. */
    private fun schemeSwatch(scheme: ColorScheme, small: Boolean = false): View {
        val p = scheme.palette
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(if (small) 6 else 8).toFloat()
                setColor(p.background)
                setStroke(1, getColor(R.color.app_separator))
            }
            layoutParams = LinearLayout.LayoutParams(dp(if (small) 40 else 56), dp(if (small) 22 else 30))
        }
        val dot = dp(if (small) 7 else 10)
        listOf(p.accent, p.label, p.secondaryLabel).forEach { color ->
            chip.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                layoutParams = LinearLayout.LayoutParams(dot, dot).apply {
                    marginStart = dp(if (small) 1 else 2)
                    marginEnd = dp(if (small) 1 else 2)
                }
            })
        }
        return chip
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val KEY_EXPANDED = "expanded_widget"

        // Shared with CalendarPermissionActivity so both know whether we've asked before.
        const val PREFS = "calendar_widget"
        const val KEY_ASKED = "asked_for_permission"
    }
}
