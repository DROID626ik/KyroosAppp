package com.kyroos.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kyroos.app.ui.screens.readKyroosConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileTileService : TileService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        val intent = Intent(this, ProfileDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch {
            val scriptDir = File(getExternalFilesDir(null), "scripts").absolutePath
            val current = readKyroosConfig(scriptDir)["profile"]?.replaceFirstChar { it.uppercase() } ?: "None"
            
            withContext(Dispatchers.Main) {
                tile.label = "KyrooS: $current"
                tile.state = if (current == "None") Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                tile.updateTile()
            }
        }
    }
}
