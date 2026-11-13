package com.radagva.widgetkit.calendar

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

/**
 * Invisible activity launched from the widget to request READ_CALENDAR.
 * If the user has permanently denied it, sends them to the app's settings instead.
 */
class CalendarPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(KEY_ASKED, false)

        when {
            CalendarEventsRepository.hasPermission(this) -> finishAndRefresh()
            askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) -> {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
                finish()
            }
            else -> {
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            finishAndRefresh()
        } else {
            finish()
        }
    }

    private fun finishAndRefresh() {
        CalendarWidgetProvider.updateAll(this)
        finish()
    }

    private companion object {
        const val PREFS = "calendar_widget"
        const val KEY_ASKED = "asked_for_permission"
        const val REQUEST_CODE = 1
    }
}
