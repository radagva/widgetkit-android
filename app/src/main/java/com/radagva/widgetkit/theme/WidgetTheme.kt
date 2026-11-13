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

/** Neovim themes the color schemes are ported from (shown in the app's credits). */
enum class ThemeFamily(val displayName: String, private val repo: String) {
    DARK_2026("Dark 2026", "code-2026-theme/nvim"),
    MOCHALATE("Mochalate", "mochalate-theme/nvim"),
    CATPPUCCIN("Catppuccin", "catppuccin/nvim"),
    GRUVBOX("Gruvbox", "ellisonleao/gruvbox.nvim"),
    KANAGAWA("Kanagawa", "rebelot/kanagawa.nvim"),
    ROSE_PINE("Rosé Pine", "rose-pine/neovim");

    val url: String get() = "https://github.com/$repo"
    val shortUrl: String get() = "github.com/$repo"
}

/** Builds a palette from 0xRRGGBB values (in role order). */
private fun palette(
    background: Long,
    label: Long,
    secondaryLabel: Long,
    mutedLabel: Long,
    accent: Long,
    onAccent: Long,
    positive: Long,
    negative: Long,
) = WidgetPalette(
    background = (0xFF000000 or background).toInt(),
    label = (0xFF000000 or label).toInt(),
    secondaryLabel = (0xFF000000 or secondaryLabel).toInt(),
    mutedLabel = (0xFF000000 or mutedLabel).toInt(),
    accent = (0xFF000000 or accent).toInt(),
    onAccent = (0xFF000000 or onAccent).toInt(),
    positive = (0xFF000000 or positive).toInt(),
    negative = (0xFF000000 or negative).toInt(),
)

/**
 * Fixed color schemes, ported from each theme's own palette file. Role mapping per theme:
 * background, text, secondary text, muted text, accent, text on accent, increase, decrease.
 * Enum names are persisted; don't rename them.
 */
enum class ColorScheme(val displayName: String, val family: ThemeFamily, val palette: WidgetPalette) {
    // code-2026: bg, fg, fg_dim, fg_muted, accent, white, ok, err
    DARK_2026("Dark 2026", ThemeFamily.DARK_2026,
        palette(0x121314, 0xBBBEBF, 0x8C8C8C, 0x555555, 0x3994BC, 0xFFFFFF, 0x7EE787, 0xF44747)),

    // mochalate: bg, fg, fg_dim, fg_muted, accent, bg, ok, err
    MOCHALATE("Mochalate", ThemeFamily.MOCHALATE,
        palette(0x1C1815, 0xE8D5C4, 0xA89278, 0x7A6850, 0xE8A050, 0x1C1815, 0x90B070, 0xE06058)),

    // catppuccin: base, text, overlay2, overlay0, mauve, base, green, red
    CATPPUCCIN_MOCHA("Catppuccin Mocha", ThemeFamily.CATPPUCCIN,
        palette(0x1E1E2E, 0xCDD6F4, 0x9399B2, 0x6C7086, 0xCBA6F7, 0x1E1E2E, 0xA6E3A1, 0xF38BA8)),
    CATPPUCCIN_MACCHIATO("Catppuccin Macchiato", ThemeFamily.CATPPUCCIN,
        palette(0x24273A, 0xCAD3F5, 0x939AB7, 0x6E738D, 0xC6A0F6, 0x24273A, 0xA6DA95, 0xED8796)),
    CATPPUCCIN_FRAPPE("Catppuccin Frappé", ThemeFamily.CATPPUCCIN,
        palette(0x303446, 0xC6D0F5, 0x949CBB, 0x737994, 0xCA9EE6, 0x303446, 0xA6D189, 0xE78284)),
    CATPPUCCIN_LATTE("Catppuccin Latte", ThemeFamily.CATPPUCCIN,
        palette(0xEFF1F5, 0x4C4F69, 0x7C7F93, 0x9CA0B0, 0x8839EF, 0xEFF1F5, 0x40A02B, 0xD20F39)),

    // gruvbox dark: dark0, light1, light4, dark4, bright_orange, dark0, bright_green, bright_red
    GRUVBOX_DARK("Gruvbox Dark", ThemeFamily.GRUVBOX,
        palette(0x282828, 0xEBDBB2, 0xA89984, 0x7C6F64, 0xFE8019, 0x282828, 0xB8BB26, 0xFB4934)),

    // gruvbox light: light0, dark1, dark4, light4, faded_orange, light0, faded_green, faded_red
    GRUVBOX_LIGHT("Gruvbox Light", ThemeFamily.GRUVBOX,
        palette(0xFBF1C7, 0x3C3836, 0x7C6F64, 0xA89984, 0xAF3A03, 0xFBF1C7, 0x79740E, 0x9D0006)),

    // kanagawa wave: sumiInk3, fujiWhite, fujiGray, sumiInk6, crystalBlue, sumiInk3, springGreen, waveRed
    KANAGAWA_WAVE("Kanagawa Wave", ThemeFamily.KANAGAWA,
        palette(0x1F1F28, 0xDCD7BA, 0x727169, 0x54546D, 0x7E9CD8, 0x1F1F28, 0x98BB6C, 0xE46876)),

    // kanagawa dragon: dragonBlack3, dragonWhite, dragonGray3, dragonBlack6, dragonBlue2, dragonBlack3, dragonGreen2, dragonRed
    KANAGAWA_DRAGON("Kanagawa Dragon", ThemeFamily.KANAGAWA,
        palette(0x181616, 0xC5C9C5, 0x7A8382, 0x625E5A, 0x8BA4B0, 0x181616, 0x8A9A7B, 0xC4746E)),

    // kanagawa lotus: lotusWhite3, lotusInk1, lotusGray2, lotusGray3, lotusBlue4, lotusWhite3, lotusGreen, lotusRed
    KANAGAWA_LOTUS("Kanagawa Lotus", ThemeFamily.KANAGAWA,
        palette(0xF2ECBC, 0x545464, 0x716E61, 0x8A8980, 0x4D699B, 0xF2ECBC, 0x6F894E, 0xC84053)),

    // rose-pine (no green): base, text, subtle, muted, iris, base, foam, love
    ROSE_PINE("Rosé Pine", ThemeFamily.ROSE_PINE,
        palette(0x191724, 0xE0DEF4, 0x908CAA, 0x6E6A86, 0xC4A7E7, 0x191724, 0x9CCFD8, 0xEB6F92)),
    ROSE_PINE_MOON("Rosé Pine Moon", ThemeFamily.ROSE_PINE,
        palette(0x232136, 0xE0DEF4, 0x908CAA, 0x6E6A86, 0xC4A7E7, 0x232136, 0x9CCFD8, 0xEB6F92)),
    ROSE_PINE_DAWN("Rosé Pine Dawn", ThemeFamily.ROSE_PINE,
        palette(0xFAF4ED, 0x464261, 0x797593, 0x9893A5, 0x907AA9, 0xFAF4ED, 0x56949F, 0xB4637A)),
}

data class WidgetTheme(val scheme: ColorScheme) {

    private val palette get() = scheme.palette

    fun textColor(views: RemoteViews, viewId: Int, role: (WidgetPalette) -> Int) =
        views.setTextColor(viewId, role(palette))

    fun tint(views: RemoteViews, viewId: Int, role: (WidgetPalette) -> Int) =
        views.setInt(viewId, "setColorFilter", role(palette))
}

/** Every widget the app registers; each can follow the global scheme or pick its own. */
enum class WidgetKind(@StringRes val label: Int) {
    CALENDAR(R.string.calendar_widget_label),
    USD(R.string.currency_usd_widget_label),
    EUR(R.string.currency_eur_widget_label),
    BOTH(R.string.currency_both_widget_label),
}

/** Global color scheme plus optional per-widget overrides. */
object WidgetSettings {

    private const val PREFS = "calendar_widget_settings"
    private const val KEY_GLOBAL = "color_scheme_global"
    private const val FOLLOW_GLOBAL = "GLOBAL"

    // Keys from earlier versions (one scheme per widget family), read for migration only.
    private const val LEGACY_CALENDAR = "color_scheme_calendar"
    private const val LEGACY_CALENDAR_OLDEST = "color_scheme"
    private const val LEGACY_CURRENCY = "color_scheme_currency"

    fun globalScheme(context: Context): ColorScheme {
        val prefs = prefs(context)
        return parse(prefs.getString(KEY_GLOBAL, null))
            ?: parse(prefs.getString(LEGACY_CALENDAR, null))
            ?: parse(prefs.getString(LEGACY_CALENDAR_OLDEST, null))
            ?: ColorScheme.DARK_2026
    }

    fun setGlobalScheme(context: Context, scheme: ColorScheme) {
        prefs(context).edit().putString(KEY_GLOBAL, scheme.name).apply()
    }

    /** The widget's own scheme, or null when it follows the global one. */
    fun override(context: Context, kind: WidgetKind): ColorScheme? {
        val stored = prefs(context).getString(overrideKey(kind), null)
        if (stored == FOLLOW_GLOBAL) return null
        parse(stored)?.let { return it }
        // Migration: currency widgets used to share their own scheme.
        if (kind != WidgetKind.CALENDAR) {
            val legacy = parse(prefs(context).getString(LEGACY_CURRENCY, null))
            if (legacy != null && legacy != globalScheme(context)) return legacy
        }
        return null
    }

    /** Pass null to follow the global scheme. */
    fun setOverride(context: Context, kind: WidgetKind, scheme: ColorScheme?) {
        prefs(context).edit().putString(overrideKey(kind), scheme?.name ?: FOLLOW_GLOBAL).apply()
    }

    fun theme(context: Context, kind: WidgetKind): WidgetTheme =
        WidgetTheme(override(context, kind) ?: globalScheme(context))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun overrideKey(kind: WidgetKind) = "color_scheme_widget_${kind.name}"

    private fun parse(value: String?): ColorScheme? =
        value?.let { runCatching { ColorScheme.valueOf(it) }.getOrNull() }
}
