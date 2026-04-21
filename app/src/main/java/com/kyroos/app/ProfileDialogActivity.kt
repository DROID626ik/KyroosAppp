package com.kyroos.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.kyroos.app.ui.screens.ProfileSelectorDialog
import com.kyroos.app.ui.screens.readKyroosConfig
import com.kyroos.app.ui.screens.updateKyroosConfig
import com.kyroos.app.ui.theme.KyroosTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scriptDir = File(getExternalFilesDir(null), "scripts").absolutePath

        setContent {
            KyroosTheme {
                var currentProfile by remember { mutableStateOf("Loading...") }
                var showDialog by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        val conf = readKyroosConfig(scriptDir)
                        val prof = conf["profile"]?.replaceFirstChar { it.uppercase() } ?: "None"
                        withContext(Dispatchers.Main) { currentProfile = prof }
                    }
                }

                if (showDialog && currentProfile != "Loading...") {
                    ProfileSelectorDialog(
                        currentProfile = currentProfile,
                        onSelect = { prof ->
                            val isFirstSetup = (currentProfile == "None")
                            val formattedProf = prof.replaceFirstChar { it.uppercase() }
                            
                            lifecycleScope.launch(Dispatchers.IO) {
                                updateKyroosConfig(scriptDir, "profile", prof)
                                if (isFirstSetup) com.kyroos.app.engine.Profile.runInitialSetup(this@ProfileDialogActivity)
                                com.kyroos.app.engine.Profile.applySystemProfile(this@ProfileDialogActivity, prof)
                                
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@ProfileDialogActivity, "KyrooS: $formattedProf Applied!", Toast.LENGTH_SHORT).show()
                                    // Beritahu Tile untuk refresh teks
                                    android.service.quicksettings.TileService.requestListeningState(
                                        this@ProfileDialogActivity,
                                        android.content.ComponentName(this@ProfileDialogActivity, ProfileTileService::class.java)
                                    )
                                    finish()
                                }
                            }
                        },
                        onDismiss = { 
                            showDialog = false
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}
