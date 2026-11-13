package com.radagva.widgetkit.currency

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.radagva.widgetkit.R
import java.math.BigDecimal
import java.util.Locale

/** Converts an amount in USD/EUR to bolívares using each of the latest rates (oficial, paralelo). */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var currency: Currency
    private lateinit var amountInput: EditText
    private lateinit var results: LinearLayout
    private lateinit var updated: TextView

    private val locale get() = Locale.getDefault()

    // Rates may refresh while the calculator is open.
    private val storeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currency = intent.getStringExtra(EXTRA_CURRENCY)
            ?.let { runCatching { Currency.valueOf(it) }.getOrNull() } ?: Currency.USD
        setContentView(R.layout.activity_calculator)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.title).setText(currency.calculatorTitle)
        findViewById<TextView>(R.id.symbol).text = currency.symbol
        findViewById<TextView>(R.id.code).text = currency.name
        results = findViewById(R.id.results)
        updated = findViewById(R.id.updated)
        amountInput = findViewById<EditText>(R.id.amount).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = render()
            })
            requestFocus()
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_CURRENCY) != currency.name) {
            setIntent(intent)
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        CurrencyStore.prefs(this).registerOnSharedPreferenceChangeListener(storeListener)
        CurrencyWidgets.refresh(this, listOf(currency))
    }

    override fun onStop() {
        CurrencyStore.prefs(this).unregisterOnSharedPreferenceChangeListener(storeListener)
        super.onStop()
    }

    private fun render() {
        val rates = CurrencyStore.lastRates(this, currency)
        val amount = parseAmount(amountInput.text.toString()) ?: BigDecimal.ONE.takeIf { amountInput.text.isBlank() }
        val format = CurrencyFormat.amount(locale)

        results.removeAllViews()
        if (rates.isEmpty()) {
            results.addView(messageRow(getString(R.string.calculator_no_rates)))
        }
        rates.forEachIndexed { index, rate ->
            val value = rate.average ?: rate.sell ?: rate.buy ?: return@forEachIndexed
            if (index > 0) results.addView(separator())
            val converted = amount?.multiply(BigDecimal.valueOf(value))
            val text = converted?.let { getString(R.string.currency_bolivares, format.format(it)) } ?: "—"
            results.addView(resultRow(
                label = rate.source.replaceFirstChar { it.titlecase(locale) },
                rate = getString(
                    R.string.calculator_rate,
                    currency.name,
                    getString(R.string.currency_bolivares, format.format(value)),
                ),
                result = text,
                copyValue = converted?.let { format.format(it) },
            ))
        }

        val state = CurrencyStore.state(this, currency)
        val time = CurrencyFormat.latestUpdate(rates)?.let { CurrencyFormat.time(this, it) }
        updated.text = when {
            state is CurrencyState.Failed && !state.refreshing && time != null ->
                getString(R.string.calculator_stale, time)
            time != null -> getString(R.string.currency_updated, time)
            else -> ""
        }
    }

    /** Accepts "1234.5", "1234,5", "1.234,50", "1,234.50". Null if not a number. */
    private fun parseAmount(raw: String): BigDecimal? {
        val text = raw.trim().replace(" ", "")
        if (text.isEmpty()) return null
        val lastSeparator = maxOf(text.lastIndexOf('.'), text.lastIndexOf(','))
        val normalized = if (lastSeparator < 0) {
            text
        } else {
            val integer = text.substring(0, lastSeparator).replace(".", "").replace(",", "")
            val fraction = text.substring(lastSeparator + 1)
            // The last separator is the decimal point; any earlier ones are grouping.
            "$integer.$fraction"
        }
        return normalized.toBigDecimalOrNull()
    }

    private fun resultRow(label: String, rate: String, result: String, copyValue: String?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (copyValue != null) {
                setOnClickListener {
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(label, copyValue))
                    Toast.makeText(this@CalculatorActivity, R.string.calculator_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(getColor(R.color.app_secondary_label))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = rate
            textSize = 13f
            setTextColor(getColor(R.color.app_secondary_label))
            gravity = Gravity.END
        })
        row.addView(header)
        row.addView(TextView(this).apply {
            text = result
            textSize = 28f
            setTextColor(getColor(R.color.app_label))
            paint.isFakeBoldText = true
            setPadding(0, dp(4), 0, 0)
        })
        return row
    }

    private fun messageRow(message: String) = TextView(this).apply {
        text = message
        textSize = 16f
        setTextColor(getColor(R.color.app_secondary_label))
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun separator() = View(this).apply {
        setBackgroundColor(getColor(R.color.app_separator))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .apply { marginStart = dp(16) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_CURRENCY = "currency"

        fun intent(context: Context, currency: Currency): Intent =
            Intent(context, CalculatorActivity::class.java)
                .putExtra(EXTRA_CURRENCY, currency.name)
                // Extras don't make intents distinct; the data URI does, so the USD and EUR
                // calculators never get mixed up when one is already open.
                .setData(Uri.parse("widgetkit://calculator/${currency.name}"))

        /** For widget taps; one PendingIntent per currency. */
        fun pendingIntent(context: Context, currency: Currency): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + currency.ordinal,
            intent(context, currency).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private const val REQUEST_CODE_BASE = 100
    }
}
