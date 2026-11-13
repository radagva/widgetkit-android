package com.radagva.widgetkit.theme

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.StringRes
import com.radagva.widgetkit.R

data class WidgetPalette(
    val background: Int,
    val label: Int,
    val secondaryLabel: Int,
    val mutedLabel: Int,
    val accent: Int,
    val onAccent: Int,
    val positive: Int,
    val negative: Int,
) {
    /** Placeholder color for loading skeletons: label blended 16% over the background. */
    val skeleton: Int get() = blend(label, background, 0.16f)

    private fun blend(fg: Int, bg: Int, amount: Float): Int {
        fun channel(shift: Int) =
            (((fg shr shift) and 0xFF) * amount + ((bg shr shift) and 0xFF) * (1 - amount)).toInt()
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/**
 * Fixed color schemes, ported from Neovim themes:
 *  - Dark 2026: https://github.com/code-2026-theme/nvim (dark palette)
 *  - Mochalate: https://github.com/mochalate-theme/nvim
 */
enum class ColorScheme(@StringRes val label: Int, val palette: WidgetPalette) {
    DARK_2026(
        R.string.scheme_dark_2026,
        WidgetPalette(
            background = 0xFF121314.toInt(), // bg
            label = 0xFFBBBEBF.toInt(), // fg
            secondaryLabel = 0xFF8C8C8C.toInt(), // fg_dim
            mutedLabel = 0xFF555555.toInt(), // fg_muted
            accent = 0xFF3994BC.toInt(), // accent
            onAccent = 0xFFFFFFFF.toInt(), // white
            positive = 0xFF7EE787.toInt(), // ok
            negative = 0xFFF44747.toInt(), // err
        ),
    ),
    MOCHALATE(
        R.string.scheme_mochalate,
        WidgetPalette(
            background = 0xFF1C1815.toInt(), // bg
            label = 0xFFE8D5C4.toInt(), // fg
            secondaryLabel = 0xFFA89278.toInt(), // fg_dim
            mutedLabel = 0xFF7A6850.toInt(), // fg_muted
            accent = 0xFFE8A050.toInt(), // accent
            onAccent = 0xFF1C1815.toInt(), // bg (dark text on amber)
            positive = 0xFF90B070.toInt(), // ok
            negative = 0xFFE06058.toInt(), // err
        ),
    ),
}

data class WidgetTheme(val scheme: ColorScheme) {

    private val palette get() = scheme.palette

    fun textColor(views: RemoteViews, viewId: Int, role: (WidgetPalette) -> Int) =
        views.setTextColor(viewId, role(palette))

    fun tint(views: RemoteViews, viewId: Int, role: (WidgetPalette) -> Int) =
        views.setInt(viewId, "setColorFilter", role(palette))
}

/** User-chosen color scheme per widget family (calendar, currency, ...). */
object WidgetSettings {

    const val CALENDAR = "calendar"
    const val CURRENCY = "currency"

    private const val PREFS = "calendar_widget_settings"
    private const val LEGACY_CALENDAR_KEY = "color_scheme"

    fun theme(context: Context, family: String): WidgetTheme {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(key(family), null)
            ?: if (family == CALENDAR) prefs.getString(LEGACY_CALENDAR_KEY, null) else null
        val scheme = stored?.let { runCatching { ColorScheme.valueOf(it) }.getOrNull() }
            ?: ColorScheme.DARK_2026
        return WidgetTheme(scheme)
    }

    fun save(context: Context, family: String, theme: WidgetTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(family), theme.scheme.name)
            .apply()
    }

    private fun key(family: String) = "color_scheme_$family"
}
