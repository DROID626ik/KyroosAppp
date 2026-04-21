package com.kyroos.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kyroos.app.ui.components.KyroosBottomNav
import com.kyroos.app.ui.screens.*
import com.kyroos.app.ui.theme.*
import com.kyroos.app.utils.ShellUtils
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { Home, Apps, ConfigMain, Tweaks, AppDetail, Opaps, GameSpace, Preload, FileManager, Editor }

class MainActivity : ComponentActivity() {

    private val SHIZUKU_PERMISSION_CODE = 1001
    private var isShizukuReadyState = mutableStateOf(false)
    private lateinit var scriptDirPath: String

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            isShizukuReadyState.value = granted
            runOnUiThread {
                if (granted) lifecycleScope.launch(Dispatchers.IO) { grantSelfWriteSecureSettings() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Pastikan izin storage diberikan agar aplikasi bisa menulis file
        checkAndRequestStoragePermission()
        
        // INI BAGIAN PENTING: Inisialisasi folder config di external files
        val scriptsFolder = File(getExternalFilesDir(null), "scripts")
        if (!scriptsFolder.exists()) scriptsFolder.mkdirs()
        scriptDirPath = scriptsFolder.absolutePath
        
        lifecycleScope.launch(Dispatchers.IO) { setupShizuku() }

        setContent {
            KyroosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KyroosApp(scriptDirPath, isShizukuReadyState.value)
                }
            }
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    private fun setupShizuku() {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
                else { isShizukuReadyState.value = true; grantSelfWriteSecureSettings() }
            }
        } catch (e: Exception) {}
    }

    private fun grantSelfWriteSecureSettings() {
        try { ShellUtils.createShizukuProcess(arrayOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS")).waitFor() } 
        catch (e: Exception) {}
    }
}

@Composable
fun KyroosApp(scriptDirPath: String, isShizukuReady: Boolean) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedAppPackage by remember { mutableStateOf("") }
    var fileToEditPath by remember { mutableStateOf("") }
    var fileManagerCurrentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }

    val mainScreens = listOf(Screen.Home, Screen.Apps, Screen.ConfigMain, Screen.FileManager)

    BackHandler(enabled = currentScreen !in mainScreens || currentScreen == Screen.Editor) {
        currentScreen = when (currentScreen) {
            Screen.Tweaks -> Screen.ConfigMain; Screen.GameSpace -> Screen.ConfigMain; Screen.Preload -> Screen.ConfigMain 
            Screen.AppDetail -> Screen.Apps; Screen.Opaps -> Screen.Tweaks; Screen.Editor -> Screen.FileManager 
            else -> Screen.Home
        }
    }

    val appVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            if (currentScreen != Screen.Editor) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentScreen !in mainScreens) {
                        IconButton(onClick = {
                            currentScreen = when (currentScreen) {
                                Screen.Tweaks -> Screen.ConfigMain; Screen.GameSpace -> Screen.ConfigMain; Screen.Preload -> Screen.ConfigMain
                                Screen.AppDetail -> Screen.Apps; Screen.Opaps -> Screen.Tweaks; else -> Screen.Home
                            }
                        }) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MdOnBg) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentScreen.name.replace("Config", " Config").replace("GameSpace", "Game Space"), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = MdOnBg)
                    } else {
                        Icon(Icons.Rounded.ViewInAr, contentDescription = "Logo", tint = MdPrimary, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("KyrooS", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MdOnBg)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = MdPrimaryContainer) {
                            Text("v$appVersion", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MdOnPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(bottom = if (currentScreen in mainScreens) 80.dp else 0.dp)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(isShizukuReady, scriptDirPath)
                        Screen.Apps -> AppsScreen { pkg -> selectedAppPackage = pkg; currentScreen = Screen.AppDetail }
                        Screen.ConfigMain -> ConfigMainScreen(scriptDirPath) { s -> currentScreen = Screen.valueOf(s) }
                        Screen.Tweaks -> TweaksScreen(scriptDirPath) { currentScreen = Screen.Opaps }
                        Screen.GameSpace -> GameSpaceScreen() // Pastikan Screen ini didefinisikan
                        Screen.Preload -> PreloadScreen() // Pastikan Screen ini didefinisikan
                        Screen.AppDetail -> AppDetailScreen(selectedAppPackage)
                        Screen.Opaps -> OpapsScreen(scriptDirPath)
                        Screen.FileManager -> FileManagerScreen(
                            rootPath = scriptDirPath,
                            currentPath = fileManagerCurrentPath,
                            onPathChange = { newPath -> fileManagerCurrentPath = newPath }
                        ) { filePath -> fileToEditPath = filePath; currentScreen = Screen.Editor }
                        Screen.Editor -> EditorScreen(fileToEditPath) { currentScreen = Screen.FileManager }
                    }
                }
            }
        }

        if (currentScreen in mainScreens) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                KyroosBottomNav(selectedTab) { idx ->
                    selectedTab = idx
                    currentScreen = when (idx) { 0 -> Screen.Home; 1 -> Screen.Apps; 2 -> Screen.ConfigMain; 3 -> Screen.FileManager; else -> Screen.Home }
                }
            }
        }
    }
}
