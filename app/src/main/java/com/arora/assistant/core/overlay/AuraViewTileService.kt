package com.arora.assistant.core.overlay

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.N)
class AuraViewTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (com.arora.assistant.core.service.ServiceStateManager.isFloatingBallActive.value) {
            stopService(Intent(this, FloatingBallService::class.java))
        } else {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FloatingBallService::class.java)
            )
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = com.arora.assistant.core.service.ServiceStateManager.isFloatingBallActive.value
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AuraView"
        tile.subtitle = if (isRunning) "Active" else "Off"
        tile.updateTile()
    }
}
