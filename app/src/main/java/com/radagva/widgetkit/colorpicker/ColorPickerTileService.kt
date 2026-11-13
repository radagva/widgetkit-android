package com.radagva.widgetkit.colorpicker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings toggle that turns the floating color picker on and off. */
class ColorPickerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ColorPickerService.isRunning) {
            ColorPickerService.stop(this)
            updateTile(running = false)
        } else if (isLocked) {
            unlockAndRun(::launchPicker)
        } else {
            launchPicker()
        }
    }

    /** Opens the (invisible) activity that asks for overlay + screen capture consent. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchPicker() {
        val intent = Intent(this, ColorPickerLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(running: Boolean = ColorPickerService.isRunning) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        /** Asks the system to refresh the tile (e.g. after the picker stops on its own). */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, ColorPickerTileService::class.java))
            }
        }
    }
}
