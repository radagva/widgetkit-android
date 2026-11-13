package com.radagva.widgetkit

import android.Manifest
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.radagva.widgetkit.calendar.CalendarEventsRepository
import com.radagva.widgetkit.calendar.CalendarWidgetProvider
import com.radagva.widgetkit.calendar.CalendarWidgetRenderer
import com.radagva.widgetkit.currency.Currency
import com.radagva.widgetkit.currency.CurrencyStore
import com.radagva.widgetkit.currency.CurrencyWidgetOptions
import com.radagva.widgetkit.currency.RateSources
import com.radagva.widgetkit.currency.CurrencyWidgetRenderer
import com.radagva.widgetkit.currency.CurrencyWidgets
import com.radagva.widgetkit.currency.DollarEuroWidgetProvider
import com.radagva.widgetkit.currency.DollarWidgetProvider
import com.radagva.widgetkit.currency.EuroWidgetProvider
import com.radagva.widgetkit.theme.ColorScheme
import com.radagva.widgetkit.theme.WidgetSettings
import com.radagva.widgetkit.theme.WidgetTheme

/** Settings screen: one section per widget family, each with live previews and a color scheme. */
class MainActivity : AppCompatActivity() {

    private lateinit var calendarTheme: WidgetTheme
    private lateinit var currencyTheme: WidgetTheme
    private lateinit var currencyOptions: CurrencyWidgetOptions
    private lateinit var accessStatus: TextView
    private lateinit var accessButton: MaterialButton

    private val requestCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        CalendarWidgetProvider.updateAll(this)
        refreshAccess()
        renderCalendarPreview()
    }

    // Re-render currency previews whenever a fetch stores new state (called on the main thread).
    private val currencyListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        renderCurrencyPreviews()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        calendarTheme = WidgetSettings.theme(this, WidgetSettings.CALENDAR)
        currencyTheme = WidgetSettings.theme(this, WidgetSettings.CURRENCY)
        currencyOptions = CurrencyWidgetOptions.load(this)
        accessStatus = findViewById(R.id.access_status)
        accessButton = findViewById(R.id.access_button)
        accessButton.setOnClickListener { onAccessClicked() }

        buildSchemeList(findViewById(R.id.scheme_list_calendar), { calendarTheme }) { scheme ->
            calendarTheme = WidgetTheme(scheme)
            WidgetSettings.save(this, WidgetSettings.CALENDAR, calendarTheme)
            CalendarWidgetProvider.updateAll(this)
            renderCalendarPreview()
        }
        buildSchemeList(findViewById(R.id.scheme_list_currency), { currencyTheme }) { scheme ->
            currencyTheme = WidgetTheme(scheme)
            WidgetSettings.save(this, WidgetSettings.CURRENCY, currencyTheme)
            CurrencyWidgets.updateAll(this)
            renderCurrencyPreviews()
        }

        buildChoiceList(
            findViewById(R.id.sources_list),
            RateSources.entries.map { it to it.label },
            { currencyOptions.sources },
        ) { sources -> updateCurrencyOptions(currencyOptions.copy(sources = sources)) }

        findViewById<SwitchMaterial>(R.id.show_change_switch).apply {
            isChecked = currencyOptions.showChange
            setOnCheckedChangeListener { _, checked ->
                updateCurrencyOptions(currencyOptions.copy(showChange = checked))
            }
        }

        buildActions(
            findViewById(R.id.actions_calendar),
            R.string.settings_add_calendar to CalendarWidgetProvider::class.java,
        )
        buildActions(
            findViewById(R.id.actions_currency),
            R.string.settings_add_usd to DollarWidgetProvider::class.java,
            R.string.settings_add_eur to EuroWidgetProvider::class.java,
            R.string.settings_add_both to DollarEuroWidgetProvider::class.java,
        )
    }

    override fun onStart() {
        super.onStart()
        CurrencyStore.prefs(this).registerOnSharedPreferenceChangeListener(currencyListener)
        // Fresh rates every time the app is opened (also updates home-screen widgets).
        CurrencyWidgets.refresh(this, Currency.entries)
    }

    override fun onStop() {
        CurrencyStore.prefs(this).unregisterOnSharedPreferenceChangeListener(currencyListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Permission may have changed in system settings while we were away.
        refreshAccess()
        renderCalendarPreview()
        renderCurrencyPreviews()
    }

    // ───────────── Previews ─────────────

    private fun renderCalendarPreview() {
        val now = System.currentTimeMillis()
        show(
            R.id.preview_calendar,
            CalendarWidgetRenderer.render(
                context = this,
                appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
                now = now,
                monthOffset = 0,
                hasPermission = CalendarEventsRepository.hasPermission(this),
                events = CalendarEventsRepository.upcomingToday(this, now),
                theme = calendarTheme,
            ),
        )
    }

    private fun renderCurrencyPreviews() {
        val previews = mapOf(
            R.id.preview_usd to DollarWidgetProvider::class.java,
            R.id.preview_eur to EuroWidgetProvider::class.java,
            R.id.preview_both to DollarEuroWidgetProvider::class.java,
        )
        CurrencyWidgets.providers.forEach { (provider, currencies) ->
            val container = previews.entries.first { it.value == provider }.key
            show(
                container,
                CurrencyWidgetRenderer.render(this, provider, currencies, currencyTheme, options = currencyOptions),
            )
        }
    }

    private fun show(containerId: Int, views: RemoteViews) {
        val container = findViewById<FrameLayout>(containerId)
        container.removeAllViews()
        container.addView(views.apply(this, container))
    }

    // ───────────── Lists ─────────────

    /** iOS-style list: [palette chip]  Name  ✓ */
    private fun buildSchemeList(
        list: LinearLayout,
        current: () -> WidgetTheme,
        onSelect: (ColorScheme) -> Unit,
    ) {
        val checks = mutableMapOf<ColorScheme, View>()
        fun refreshChecks() = checks.forEach { (scheme, check) ->
            check.visibility = if (scheme == current().scheme) View.VISIBLE else View.INVISIBLE
        }

        list.removeAllViews()
        ColorScheme.entries.forEachIndexed { index, scheme ->
            if (index > 0) list.addView(separator())
            val row = listRow {
                if (scheme != current().scheme) {
                    onSelect(scheme)
                    refreshChecks()
                }
            }
            row.addView(schemeSwatch(scheme))
            row.addView(TextView(this).apply {
                setText(scheme.label)
                textSize = 16f
                setTextColor(getColor(R.color.app_label))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(12) }
            })
            val check = checkMark()
            checks[scheme] = check
            row.addView(check)
            list.addView(row)
        }
        refreshChecks()
    }

    private fun updateCurrencyOptions(options: CurrencyWidgetOptions) {
        if (options == currencyOptions) return
        currencyOptions = options
        CurrencyWidgetOptions.save(this, options)
        CurrencyWidgets.updateAll(this)
        renderCurrencyPreviews()
    }

    /** iOS-style single-choice list: Label  ✓ */
    private fun <T> buildChoiceList(
        list: LinearLayout,
        options: List<Pair<T, Int>>,
        current: () -> T,
        onSelect: (T) -> Unit,
    ) {
        val checks = mutableMapOf<T, View>()
        fun refreshChecks() = checks.forEach { (option, check) ->
            check.visibility = if (option == current()) View.VISIBLE else View.INVISIBLE
        }
        list.removeAllViews()
        options.forEachIndexed { index, (option, label) ->
            if (index > 0) list.addView(separator())
            val row = listRow {
                if (option != current()) {
                    onSelect(option)
                    refreshChecks()
                }
            }
            row.addView(TextView(this).apply {
                setText(label)
                textSize = 16f
                setTextColor(getColor(R.color.app_label))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val check = checkMark()
            checks[option] = check
            row.addView(check)
            list.addView(row)
        }
        refreshChecks()
    }

    private fun checkMark() = TextView(this).apply {
        text = "✓"
        textSize = 17f
        setTextColor(getColor(R.color.app_tint))
    }

    private fun buildActions(list: LinearLayout, vararg actions: Pair<Int, Class<out AppWidgetProvider>>) {
        list.removeAllViews()
        actions.forEachIndexed { index, (@StringRes label, provider) ->
            if (index > 0) list.addView(separator())
            val row = listRow { pinWidget(provider) }
            row.addView(TextView(this).apply {
                setText(label)
                textSize = 16f
                setTextColor(getColor(R.color.app_tint))
            })
            list.addView(row)
        }
    }

    private fun listRow(onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(getColor(R.color.app_separator))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .apply { marginStart = dp(16) }
    }

    /** A tiny widget-like chip: scheme background with accent, label and secondary dots. */
    private fun schemeSwatch(scheme: ColorScheme): View {
        val p = scheme.palette
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(p.background)
                setStroke(1, getColor(R.color.app_separator))
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(30))
        }
        listOf(p.accent, p.label, p.secondaryLabel).forEach { color ->
            chip.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            })
        }
        return chip
    }

    private fun pinWidget(provider: Class<out AppWidgetProvider>) {
        val manager = AppWidgetManager.getInstance(this)
        val pinned = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(this, provider), null, null)
        if (!pinned) {
            Toast.makeText(this, R.string.settings_add_widget_manual, Toast.LENGTH_LONG).show()
        }
    }

    // ───────────── Calendar access ─────────────

    private fun onAccessClicked() {
        if (accessButton.tag == OPEN_SETTINGS) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
            requestCalendar.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun refreshAccess() {
        val granted = CalendarEventsRepository.hasPermission(this)
        accessStatus.setText(if (granted) R.string.settings_access_granted else R.string.settings_access_missing)
        accessButton.visibility = if (granted) View.GONE else View.VISIBLE

        // After a permanent denial the system won't show the dialog again; send to Settings.
        val asked = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ASKED, false)
        val blocked = !granted && asked && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)
        accessButton.tag = if (blocked) OPEN_SETTINGS else null
        accessButton.setText(if (blocked) R.string.settings_access_open_settings else R.string.settings_access_allow)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        // Shared with CalendarPermissionActivity so both know whether we've asked before.
        const val PREFS = "calendar_widget"
        const val KEY_ASKED = "asked_for_permission"
        const val OPEN_SETTINGS = "open_settings"
    }
}
