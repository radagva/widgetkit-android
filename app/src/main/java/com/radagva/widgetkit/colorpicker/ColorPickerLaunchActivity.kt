package com.radagva.widgetkit.colorpicker

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.radagva.widgetkit.R

/**
 * Invisible activity started by the Quick Settings tile: makes sure the app may draw over
 * other apps, asks for screen capture consent, then starts [ColorPickerService].
 */
class ColorPickerLaunchActivity : ComponentActivity() {

    private val screenCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ColorPickerService.start(this, result.resultCode, data)
        } else {
            ColorPickerTileService.requestUpdate(this)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // consent dialog already showing

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.color_picker_overlay_needed, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }

        val manager = getSystemService(MediaProjectionManager::class.java)
        val consent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The picker needs the whole screen, not a single app.
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        screenCapture.launch(consent)
    }
}
