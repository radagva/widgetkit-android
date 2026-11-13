package com.radagva.widgetkit.currency

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.radagva.widgetkit.R
import com.radagva.widgetkit.theme.WidgetTheme
import java.util.Locale

/** Builds currency widgets: one column per currency, side by side like the calendar widget. */
object CurrencyWidgetRenderer {

    /** dolarapi returns two rates (oficial, paralelo) per currency. */
    private const val DEFAULT_SKELETON_ROWS = 2

    fun render(
        context: Context,
        provider: Class<out CurrencyWidgetProvider>,
        currencies: List<Currency>,
        theme: WidgetTheme,
        states: Map<Currency, CurrencyState> = currencies.associateWith { CurrencyStore.state(context, it) },
        options: CurrencyWidgetOptions,
    ): RemoteViews {
        val root = RemoteViews(context.packageName, R.layout.widget_currency)
        theme.tint(root, R.id.widget_bg) { it.background }

        val refresh = refreshIntent(context, provider)
        root.removeAllViews(R.id.columns)
        currencies.forEachIndexed { index, currency ->
            if (index > 0) root.addView(R.id.columns, RemoteViews(context.packageName, R.layout.widget_currency_gap))
            val column = renderColumn(
                context = context,
                theme = theme,
                currency = currency,
                state = states.getValue(currency),
                options = options,
                refresh = refresh,
                // One refresh button per widget, top-right.
                showRefreshButton = index == currencies.lastIndex,
            )
            root.addView(R.id.columns, column)
        }
        return root
    }

    private fun renderColumn(
        context: Context,
        theme: WidgetTheme,
        currency: Currency,
        state: CurrencyState,
        options: CurrencyWidgetOptions,
        refresh: PendingIntent,
        showRefreshButton: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_currency_column)
        val locale = Locale.getDefault()

        views.setTextViewText(R.id.title, context.getString(currency.title))
        views.setOnClickPendingIntent(R.id.column, CalculatorActivity.pendingIntent(context, currency))
        theme.textColor(views, R.id.title) { it.accent }
        theme.textColor(views, R.id.updated) { it.secondaryLabel }
        theme.tint(views, R.id.refresh) { it.accent }

        val refreshing = when (state) {
            is CurrencyState.Loading -> true
            is CurrencyState.Loaded -> state.refreshing
            is CurrencyState.Failed -> state.refreshing
        }

        // Header: refresh button.
        if (showRefreshButton) {
            views.setViewVisibility(R.id.refresh_slot, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.refresh_slot, refresh)
        } else {
            views.setViewVisibility(R.id.refresh_slot, View.GONE)
        }

        views.removeAllViews(R.id.rates)
        views.setViewVisibility(R.id.rates, View.GONE)
        views.setViewVisibility(R.id.error, View.GONE)
        views.setViewVisibility(R.id.skeleton, View.GONE)
        views.setViewVisibility(R.id.updated, View.VISIBLE)
        views.setTextViewText(R.id.updated, "")

        when {
            refreshing -> {
                // Skeleton replaces the content (and footer) while fetching.
                val rows = (state as? CurrencyState.Loaded)?.rates?.let { options.sources.filter(it).size }
                    ?.coerceIn(1, 3)
                    ?: if (options.sources == RateSources.BOTH) DEFAULT_SKELETON_ROWS else 1
                views.setViewVisibility(R.id.skeleton, View.VISIBLE)
                views.setViewVisibility(R.id.updated, View.GONE)
                views.removeAllViews(R.id.skeleton)
                views.addView(R.id.skeleton, renderSkeleton(context, theme, R.layout.widget_currency_skeleton, rows))
                views.addView(R.id.skeleton, renderSkeleton(context, theme, R.layout.widget_currency_skeleton_dim, rows))
            }
            state is CurrencyState.Loaded -> {
                val rates = options.sources.filter(state.rates)
                views.setViewVisibility(R.id.rates, View.VISIBLE)
                rates.forEach {
                    views.addView(R.id.rates, renderRate(context, theme, currency, it, locale, options.showChange))
                }
                CurrencyFormat.latestUpdate(rates)?.let { time ->
                    views.setTextViewText(R.id.updated, context.getString(R.string.currency_updated, CurrencyFormat.time(context, time)))
                } ?: rates.firstOrNull()?.let {
                    views.setTextViewText(R.id.updated, context.getString(R.string.currency_updated, it.updatedAt))
                }
            }
            state is CurrencyState.Failed -> {
                views.setViewVisibility(R.id.error, View.VISIBLE)
                val (icon, message) = when (state.error) {
                    FetchError.NO_CONNECTION -> R.drawable.ic_widget_no_connection to R.string.currency_error_no_connection
                    FetchError.NOT_AVAILABLE -> R.drawable.ic_widget_question to R.string.currency_error_not_available
                    FetchError.PARSE -> R.drawable.ic_widget_hammer to R.string.currency_error_parse
                }
                views.setImageViewResource(R.id.error_icon, icon)
                views.setTextViewText(R.id.error_message, context.getString(message))
                theme.tint(views, R.id.error_icon) { it.secondaryLabel }
                theme.textColor(views, R.id.error_message) { it.secondaryLabel }
                theme.tint(views, R.id.retry_bg) { it.accent }
                theme.textColor(views, R.id.retry_label) { it.onAccent }
                views.setOnClickPendingIntent(R.id.retry, refresh)
            }
            else -> Unit
        }
        return views
    }

    private fun renderSkeleton(context: Context, theme: WidgetTheme, layout: Int, rows: Int): RemoteViews {
        val frame = RemoteViews(context.packageName, layout)
        theme.tint(frame, R.id.bar_footer) { it.skeleton }
        frame.removeAllViews(R.id.skeleton_rows)
        repeat(rows) {
            val row = RemoteViews(context.packageName, R.layout.widget_currency_skeleton_row)
            theme.tint(row, R.id.bar_label) { it.skeleton }
            theme.tint(row, R.id.bar_value) { it.skeleton }
            frame.addView(R.id.skeleton_rows, row)
        }
        return frame
    }

    private fun renderRate(
        context: Context,
        theme: WidgetTheme,
        currency: Currency,
        rate: CurrencyRate,
        locale: Locale,
        showChange: Boolean,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_currency_rate)
        val format = CurrencyFormat.amount(locale)
        fun money(value: Double) = context.getString(R.string.currency_bolivares, format.format(value))

        row.setTextViewText(R.id.rate_label, CurrencyFormat.sourceLabel(context, rate.source, locale))
        row.setTextViewText(R.id.rate_value, rate.value?.let(::money) ?: "—")
        theme.textColor(row, R.id.rate_label) { it.secondaryLabel }
        theme.textColor(row, R.id.rate_value) { it.label }

        // Change since the previous value: "+12.34" / "-5.10".
        CurrencyStore.change(context, currency, rate)?.takeIf { showChange }?.let { delta ->
            val rounded = format.format(kotlin.math.abs(delta))
            val zero = format.format(0.0)
            row.setViewVisibility(R.id.rate_change, View.VISIBLE)
            row.setTextViewText(
                R.id.rate_change,
                when {
                    rounded == zero -> rounded
                    delta > 0 -> "+$rounded"
                    else -> "-$rounded"
                },
            )
            theme.textColor(row, R.id.rate_change) {
                when {
                    rounded == zero -> it.secondaryLabel
                    delta > 0 -> it.positive
                    else -> it.negative
                }
            }
        }

        if (rate.buy != null && rate.sell != null) {
            row.setViewVisibility(R.id.rate_detail, View.VISIBLE)
            row.setTextViewText(
                R.id.rate_detail,
                context.getString(R.string.currency_buy_sell, format.format(rate.buy), format.format(rate.sell)),
            )
            theme.textColor(row, R.id.rate_detail) { it.secondaryLabel }
        }
        return row
    }

    private fun refreshIntent(context: Context, provider: Class<out CurrencyWidgetProvider>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, provider).setAction(CurrencyWidgets.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
