package com.radagva.widgetkit.uuid

import android.content.Context
import android.content.SharedPreferences

/** Selected UUID version and the current UUID, shared by the widget and the app section. */
object UuidStore {

    const val PREFS = "uuid_generator"
    private const val KEY_VERSION = "version"
    private const val KEY_VALUE = "value"

    data class State(val version: UuidVersion, val value: String)

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): State {
        val prefs = prefs(context)
        val version = prefs.getString(KEY_VERSION, null)
            ?.let { runCatching { UuidVersion.valueOf(it) }.getOrNull() } ?: UuidVersion.V7
        val value = prefs.getString(KEY_VALUE, null) ?: return generate(context, version)
        return State(version, value)
    }

    /** Generates (and stores) a new UUID of [version], defaulting to the selected one. */
    fun generate(context: Context, version: UuidVersion? = null): State {
        val chosen = version ?: state(context).version
        val value = UuidGenerator.generate(chosen).toString()
        prefs(context).edit().putString(KEY_VERSION, chosen.name).putString(KEY_VALUE, value).apply()
        return State(chosen, value)
    }
}
