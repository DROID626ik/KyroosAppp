package com.kyroos.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import android.os.StatFs
import android.os.Environment
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import com.kyroos.app.ui.components.*
import com.kyroos.app.ui.theme.*
import com.kyroos.app.utils.ShellUtils
import com.kyroos.app.utils.toImageBitmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.math.roundToInt

// ==========================================
// STATE & DATA MODELS
// ==========================================
object AppCache {
    var cachedApps by mutableStateOf<List<AppItem>>(emptyList())
    var githubAvatar by mutableStateOf<ImageBitmap?>(null)
    var socInfo: String? = null
    var ramInfo: String? = null
    var kernelInfo: String? = null
}

data class AppItem(
    val name: String, 
    val packageName: String, 
    val isSystem: Boolean,
    val isGame: Boolean
)

data class CpuCoreInfo(
    val index: Int,
    val frequency: String,
    val isOnline: Boolean
)

// ==========================================
// CONFIGURATIONS ENGINE HELPERS
// ==========================================
fun readKyroosConfig(dirPath: String): Map<String, String> {
    val file = File(dirPath, "kyroos.conf")
    if (!file.exists()) return emptyMap()
    return try {
        file.readLines().mapNotNull {
            val parts = it.split("=")
            if (parts.size >= 2) parts[0].trim() to parts[1].trim() else null
        }.toMap()
    } catch (e: Exception) { emptyMap() }
}

fun updateKyroosConfig(dirPath: String, key: String, value: String) {
    val file = File(dirPath, "kyroos.conf")
    try {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val lines = file.readLines().toMutableList()
        var found = false
        for (i in lines.indices) {
            if (lines[i].startsWith("$key=")) {
                lines[i] = "$key=$value"
                found = true
                break
            }
        }
        if (!found) lines.add("$key=$value")
        file.writeText(lines.joinToString("\n"))
    } catch (e: Exception) { e.printStackTrace() }
}

fun updateAppListInConfig(scriptDirPath: String, key: String, packageName: String, isAdded: Boolean): String {
    val conf = readKyroosConfig(scriptDirPath)
    val currentList = conf[key]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
    if (isAdded) {
        if (!currentList.contains(packageName)) currentList.add(packageName)
    } else {
        currentList.remove(packageName)
    }
    val newListString = currentList.joinToString(",")
    updateKyroosConfig(scriptDirPath, key, newListString)
    return newListString
}

// ==========================================
// HARDWARE INFO HELPERS (ANDROID API NATIVE)
// ==========================================
fun getTotalRam(context: Context): Long {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    return memInfo.totalMem
}

fun getAvailableRam(context: Context): Long {
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val memInfo = android.app.ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    return memInfo.availMem
}

fun getStorageInfo(): Pair<Long, Long> {
    val stat = StatFs(Environment.getDataDirectory().path)
    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong
    val total = totalBlocks * blockSize
    val available = availableBlocks * blockSize
    val used = total - available
    return Pair(used, total)
}

fun getBatteryInfo(context: Context): Triple<Int, Float, Boolean> {
    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
    val percentage = (level * 100 / scale.toFloat()).toInt()
    val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Triple(percentage, temperature, isCharging)
}

fun getScreenRefreshRate(context: Context): Float {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
    }
    return display?.refreshRate ?: 60f
}

fun getGpuRenderer(): String {
    return try {
        val process = Runtime.getRuntime().exec("getprop ro.hardware.egl")
        process.inputStream.bufferedReader().readLine()?.trim() ?: "Unknown"
    } catch (e: Exception) { "Unknown" }
}

fun getSocName(): String {
    return try {
        val process = Runtime.getRuntime().exec("getprop ro.hardware")
        process.inputStream.bufferedReader().readLine()?.trim()?.uppercase() ?: Build.HARDWARE.uppercase()
    } catch (e: Exception) { Build.HARDWARE.uppercase() }
}
// ==========================================
// HOME SCREEN LENGKAP (MD3 - ZRAM ATAS, CPU, DEVICE INFO NATIVE)
// ==========================================
@Composable
fun HomeScreen(isShizukuReady: Boolean, scriptDirPath: String) {
    val context = LocalContext.current
    
    // States untuk semua info
    var cpuCores by remember { mutableStateOf(List(8) { CpuCoreInfo(it, "Offline", false) }) }
    
    var usedRamStr by remember { mutableStateOf("0.00 GB") }
    var totalRamStr by remember { mutableStateOf("0.00 GB") }
    var ramPercent by remember { mutableStateOf(0f) }
    
    var usedZramStr by remember { mutableStateOf("0.00 GB") }
    var totalZramStr by remember { mutableStateOf("0.00 GB") }
    var zramPercent by remember { mutableStateOf(0f) }
    
    var usedStorageStr by remember { mutableStateOf("0.00 GB") }
    var totalStorageStr by remember { mutableStateOf("0.00 GB") }
    var storagePercent by remember { mutableStateOf(0f) }
    
    var batteryPercent by remember { mutableStateOf(0) }
    var batteryTemp by remember { mutableStateOf(0f) }
    var isCharging by remember { mutableStateOf(false) }
    
    var uptimeStr by remember { mutableStateOf("0j 0m") }
    var deepSleepStr by remember { mutableStateOf("0j 0m") }
    
    var screenResolution by remember { mutableStateOf("Unknown") }
    var refreshRate by remember { mutableStateOf("60Hz") }
    var gpuRenderer by remember { mutableStateOf("Unknown") }
    
    var totalApps by remember { mutableStateOf(0) }
    var userApps by remember { mutableStateOf(0) }
    var systemApps by remember { mutableStateOf(0) }
    
    var cpuUsageHistory by remember { mutableStateOf(List(20) { 0f }) }

    LaunchedEffect(Unit) {
        // Init data statis (Apps, Layar, Storage)
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            withContext(Dispatchers.Main) {
                totalApps = packages.size
                userApps = packages.count { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                systemApps = totalApps - userApps
                
                val metrics = context.resources.displayMetrics
                screenResolution = "${metrics.widthPixels} x ${metrics.heightPixels}"
                refreshRate = "${getScreenRefreshRate(context).toInt()}Hz"
                gpuRenderer = getGpuRenderer()
                
                val (usedStorage, totalStorage) = getStorageInfo()
                usedStorageStr = String.format("%.2f GB", usedStorage / (1024f * 1024f * 1024f))
                totalStorageStr = String.format("%.2f GB", totalStorage / (1024f * 1024f * 1024f))
                storagePercent = usedStorage.toFloat() / totalStorage.toFloat()
            }
        }
        
        // Loop update dinamis (NO LAG)
        while (isActive) {
            // RAM
            val totalRam = getTotalRam(context)
            val availRam = getAvailableRam(context)
            val usedRam = totalRam - availRam
            totalRamStr = String.format("%.2f GB", totalRam / (1024f * 1024f * 1024f))
            usedRamStr = String.format("%.2f GB", usedRam / (1024f * 1024f * 1024f))
            ramPercent = usedRam.toFloat() / totalRam.toFloat()

            // ZRAM
            withContext(Dispatchers.IO) {
                try {
                    val memlines = File("/proc/meminfo").readLines()
                    var sTotal = 0f
                    var sFree = 0f
                    for (line in memlines) {
                        if (line.startsWith("SwapTotal:")) sTotal = line.split(Regex("\\s+"))[1].toFloat()
                        if (line.startsWith("SwapFree:")) sFree = line.split(Regex("\\s+"))[1].toFloat()
                    }
                    val tZ = sTotal / (1024 * 1024)
                    val uZ = (sTotal - sFree) / (1024 * 1024)
                    withContext(Dispatchers.Main) {
                        usedZramStr = String.format("%.2f GB", uZ)
                        totalZramStr = String.format("%.2f GB", tZ)
                        zramPercent = if (tZ > 0) uZ / tZ else 0f
                    }
                } catch (e: Exception) {}
            }
            
            // Battery & Uptime
            val (batPct, batTemp, charging) = getBatteryInfo(context)
            batteryPercent = batPct
            batteryTemp = batTemp
            isCharging = charging
            
            val uptimeMillis = android.os.SystemClock.elapsedRealtime()
            uptimeStr = "${uptimeMillis / 3600000}j ${(uptimeMillis / 60000) % 60}m"
            
            val sleepMillis = android.os.SystemClock.elapsedRealtime() - android.os.SystemClock.uptimeMillis()
            deepSleepStr = "${sleepMillis / 3600000}j ${(sleepMillis / 60000) % 60}m"
            
            // CPU via File API
            withContext(Dispatchers.IO) {
                val cores = mutableListOf<CpuCoreInfo>()
                var totalUsage = 0f
                
                for (i in 0..7) {
                    try {
                        val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                        val onlineFile = File("/sys/devices/system/cpu/cpu$i/online")
                        
                        val isOnline = if (onlineFile.exists()) onlineFile.readText().trim() == "1" else true
                        
                        if (isOnline && freqFile.exists()) {
                            val freq = freqFile.readText().trim().toLong()
                            cores.add(CpuCoreInfo(i, "${freq / 1000} MHz", true))
                            
                            val maxFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                            if (maxFreqFile.exists()) {
                                val maxFreq = maxFreqFile.readText().trim().toLong()
                                totalUsage += (freq.toFloat() / maxFreq.toFloat())
                            }
                        } else {
                            cores.add(CpuCoreInfo(i, "Offline", false))
                        }
                    } catch (e: Exception) { 
                        cores.add(CpuCoreInfo(i, "Offline", false))
                    }
                }
                
                val avgUsage = if (cores.count { it.isOnline } > 0) totalUsage / cores.count { it.isOnline } else 0f
                
                withContext(Dispatchers.Main) {
                    cpuUsageHistory = cpuUsageHistory.drop(1) + avgUsage
                    cpuCores = cores
                }
            }
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // POSISI 1: RAM & ZRAM Paling Atas
        MemoryDashboardCard(
            usedRam = usedRamStr, totalRam = totalRamStr, ramPct = ramPercent,
            usedZram = usedZramStr, totalZram = totalZramStr, zramPct = zramPercent
        )

        // POSISI 2: CPU Graph & Status
        CpuStatusCardWithGraph(cores = cpuCores, usageHistory = cpuUsageHistory)
        
        // POSISI 3: Dashboard Grid (Battery, Apps, Display, Storage)
        DashboardGrid(
            batteryPercent = batteryPercent, batteryTemp = batteryTemp, isCharging = isCharging,
            storagePercent = storagePercent, usedStorage = usedStorageStr, totalStorage = totalStorageStr,
            totalApps = totalApps, userApps = userApps, systemApps = systemApps,
            screenResolution = screenResolution, refreshRate = refreshRate, gpuRenderer = gpuRenderer
        )
        
        // POSISI 4: Device Info (API Native Android)
        val brandNative = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val modelNative = Build.MODEL
        val socNameNative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.uppercase() else Build.HARDWARE.uppercase()
        val kyroosVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
        
        DeviceInfoCompleteCard(
            brand = brandNative, model = modelNative, socName = socNameNative,
            androidVersion = "Android ${Build.VERSION.RELEASE}", uptime = uptimeStr, deepSleep = deepSleepStr, kyroosVersion = kyroosVersion
        )
        
        // 🔥 Fix scroll kedodoran: Spacer dikecilkan jadi 24.dp
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// CONFIG MAIN SCREEN & ENGINE DIRECT CALL
// ==========================================
@Composable
fun ConfigMainScreen(scriptDirPath: String, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profileDialog by remember { mutableStateOf(false) }
    var showDevModal by remember { mutableStateOf(false) }
    var currentProfile by remember { mutableStateOf("None") }

    var isFpsEnabled by remember { mutableStateOf(com.kyroos.app.engine.FpsService.isRunning) }

    val currentProfileIcon = when (currentProfile.lowercase()) {
        "balanced" -> Icons.Rounded.Balance
        "powersave" -> Icons.Rounded.BatteryChargingFull
        "gaming" -> Icons.Rounded.Gamepad
        "extreme" -> Icons.Rounded.Bolt
        else -> Icons.Rounded.Block 
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val conf = readKyroosConfig(scriptDirPath)
            val prof = conf["profile"]?.replaceFirstChar { it.uppercase() } ?: "None"
            withContext(Dispatchers.Main) { currentProfile = prof }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(20.dp))

        // HERO BANNER: System Profile
        Card(
            modifier = Modifier.fillMaxWidth().bounceClick { profileDialog = true },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MdPrimaryContainer)
        ) {
            Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("System Profile", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MdOnPrimaryContainer)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(currentProfileIcon, contentDescription = null, tint = MdOnPrimaryContainer.copy(alpha=0.8f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Current: $currentProfile", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MdOnPrimaryContainer.copy(alpha=0.8f))
                    }
                }
                Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(MdOnPrimaryContainer.copy(alpha=0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = MdOnPrimaryContainer, modifier = Modifier.size(26.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FPS METER OVERLAY
        ConfigCardSwitch(title = "Live FPS Monitor", subtitle = "Native screen frame tracking", icon = Icons.Rounded.MonitorHeart, checked = isFpsEnabled) { checked ->
            if (checked) {
                if (!android.provider.Settings.canDrawOverlays(context)) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                    Toast.makeText(context, "Please allow 'Display over other apps' first!", Toast.LENGTH_LONG).show()
                } else {
                    context.startService(Intent(context, com.kyroos.app.engine.FpsService::class.java))
                    isFpsEnabled = true
                }
            } else {
                context.stopService(Intent(context, com.kyroos.app.engine.FpsService::class.java))
                isFpsEnabled = false
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GRID LAYOUT UNTUK MENU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardGridCard(title = "Game Space", subtitle = "CMD Interventions", icon = Icons.Rounded.SportsEsports, onClick = { onNavigate("GameSpace") }, modifier = Modifier.weight(1f))
            DashboardGridCard(title = "Preload Utility", subtitle = "Kasane Injector", icon = Icons.Rounded.Memory, onClick = { onNavigate("Preload") }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardGridCard(title = "Extra Tweaks", subtitle = "GPU & Caches", icon = Icons.Rounded.Build, onClick = { onNavigate("Tweaks") }, modifier = Modifier.weight(1f))
            DashboardGridCard(title = "Developer", subtitle = "KonekoDev", icon = Icons.Rounded.Code, onClick = { showDevModal = true }, modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (profileDialog) {
        ProfileSelectorDialog(
            currentProfile = currentProfile, 
            onSelect = { prof -> 
                val isFirstSetup = (currentProfile == "None")
                val selectedProf = prof.lowercase() // "balanced", "gaming", etc.
                
                // Update UI state
                currentProfile = prof.replaceFirstChar { it.uppercase() }
                profileDialog = false
                
                // 🔥 SEMUA operasi file & shell di IO thread
                scope.launch(Dispatchers.IO) { 
                    try {
                        // 1. Simpan ke config file
                        updateKyroosConfig(scriptDirPath, "profile", selectedProf)
                        
                        // 2. Verify (optional debug)
                        val verify = readKyroosConfig(scriptDirPath)
                        android.util.Log.d("Kyroos", "Verified config: profile=${verify["profile"]}")
                        
                        // 3. Apply profile via engine
                        if (isFirstSetup) {
                            com.kyroos.app.engine.Profile.runInitialSetup(context)
                        }
                        com.kyroos.app.engine.Profile.applySystemProfile(context, selectedProf)
                        
                        withContext(Dispatchers.Main) { 
                            Toast.makeText(context, "Profile $currentProfile Applied!", Toast.LENGTH_SHORT).show() 
                        }
                    } catch (e: Exception) { 
                        android.util.Log.e("Kyroos", "Profile apply failed: ${e.message}")
                        e.printStackTrace() 
                    }
                }
            }, 
            onDismiss = { profileDialog = false }
        )
    }
    
    if (showDevModal) DevProfileModal(onDismiss = { showDevModal = false })
}
// ==========================================
// 1. MEMORY DASHBOARD (RAM & ZRAM) - MD3
// ==========================================
@Composable
fun MemoryDashboardCard(
    usedRam: String, totalRam: String, ramPct: Float,
    usedZram: String, totalZram: String, zramPct: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MdPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Memory, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text("Memory & Swap", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // RAM Bar
            MemoryBarItem("System RAM", usedRam, totalRam, ramPct, MdPrimary)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 🔥 ZRAM Bar: Warnanya sekarang pakai MdPrimary biar sama persis dengan RAM!
            MemoryBarItem("ZRAM / Swap", usedZram, totalZram, zramPct, MdPrimary)
        }
    }
}

@Composable
fun MemoryBarItem(label: String, used: String, total: String, pct: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MdOnBg)
            Text("$used / $total", fontSize = 11.sp, color = MdOutline, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        val animatedPct by animateFloatAsState(targetValue = pct, animationSpec = tween(800), label = "memAnim")
        LinearProgressIndicator(
            progress = { animatedPct },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = color,
            trackColor = MdSurfaceContainerHigh,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

// ==========================================
// 2. CPU STATUS & GRAPH - MD3
// ==========================================
@Composable
fun CpuStatusCardWithGraph(cores: List<CpuCoreInfo>, usageHistory: List<Float>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Speed, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("CPU Cores Activity", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                val avgUsage = if (usageHistory.isNotEmpty()) (usageHistory.average() * 100).toInt() else 0
                val animatedUsage by animateIntAsState(targetValue = avgUsage, animationSpec = tween(500), label = "usageAnim")
                val usageColor by animateColorAsState(targetValue = if (avgUsage > 75) MaterialTheme.colorScheme.error else MdPrimary, animationSpec = tween(300), label = "usageColor")
                
                Surface(shape = RoundedCornerShape(10.dp), color = usageColor.copy(alpha = 0.15f)) {
                    Text("$animatedUsage%", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = usageColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            CpuCoresWithGraphBackground(cores = cores, usageHistory = usageHistory)
        }
    }
}

@Composable
fun CpuCoresWithGraphBackground(cores: List<CpuCoreInfo>, usageHistory: List<Float>) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MdSurfaceContainerHigh)) {
        CpuBackgroundGraph(usageHistory = usageHistory)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0..3) CpuCoreItem(core = cores.getOrElse(i) { CpuCoreInfo(i, "Offline", false) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 4..7) CpuCoreItem(core = cores.getOrElse(i) { CpuCoreInfo(i, "Offline", false) })
            }
        }
    }
}

@Composable
fun CpuBackgroundGraph(usageHistory: List<Float>) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(usageHistory) { animatedProgress.animateTo(targetValue = 1f, animationSpec = tween(800)) }
    Box(modifier = Modifier.fillMaxWidth().height(105.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val barWidth = width / usageHistory.size
            usageHistory.forEachIndexed { index, usage ->
                val animatedUsage = usage * animatedProgress.value
                val barHeight = animatedUsage * height * 0.70f 
                val x = index * barWidth
                val y = height - barHeight
                val barColor = MdPrimary.copy(alpha = 0.05f + (animatedUsage * 0.15f))
                drawRoundRect(color = barColor, topLeft = Offset(x + 2f, y), size = Size(barWidth - 4f, barHeight), cornerRadius = CornerRadius(4f, 4f))
            }
        }
    }
}

@Composable
fun CpuCoreItem(core: CpuCoreInfo) {
    var displayedFreq by remember { mutableStateOf(core.frequency) }
    LaunchedEffect(core.frequency) { if (displayedFreq != core.frequency) displayedFreq = core.frequency }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(65.dp)) {
        Text("CPU${core.index}", color = MdOutline, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedContent(targetState = displayedFreq, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) }, label = "freqAnim") { freq ->
            Text(freq, color = if (core.isOnline) MdOnBg else MdOutline.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ==========================================
// 3. DASHBOARD GRID (BATERAI, APPS, DISPLAY, STORAGE)
// ==========================================
@Composable
fun DashboardGrid(
    batteryPercent: Int, batteryTemp: Float, isCharging: Boolean,
    storagePercent: Float, usedStorage: String, totalStorage: String,
    totalApps: Int, userApps: Int, systemApps: Int,
    screenResolution: String, refreshRate: String, gpuRenderer: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Baris 1: Battery & Apps
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardItem(title = "Battery", icon = Icons.Rounded.BatteryFull, modifier = Modifier.weight(1f)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val animatedBattery by animateIntAsState(targetValue = batteryPercent, animationSpec = tween(600), label = "batteryAnim")
                        Text("$animatedBattery%", color = if (batteryPercent <= 20) MaterialTheme.colorScheme.error else MdPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${batteryTemp.roundToInt()}°C", color = MdOutline, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(if (isCharging) "Charging" else "Discharging", color = MdOutline, fontSize = 12.sp)
                }
            }
            
            DashboardItem(title = "Apps", icon = Icons.Rounded.Apps, modifier = Modifier.weight(1f)) {
                Column {
                    val animatedApps by animateIntAsState(targetValue = totalApps, animationSpec = tween(800), label = "appsAnim")
                    Text("$animatedApps", color = MdPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("$userApps User • $systemApps System", color = MdOutline, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        // Baris 2: Display & Storage
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardItem(title = "Display", icon = Icons.Rounded.Smartphone, modifier = Modifier.weight(1f)) {
                Column {
                    Text(gpuRenderer.take(15), color = MdOnBg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$screenResolution • $refreshRate", color = MdOutline, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            DashboardItem(title = "Storage", icon = Icons.Rounded.Storage, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(usedStorage, color = MdOnBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("of $totalStorage", color = MdOutline, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        val animatedStorage by animateFloatAsState(targetValue = storagePercent, animationSpec = tween(800), label = "storageAnim")
                        CircularProgressIndicator(progress = { animatedStorage }, color = MdPrimary, trackColor = MdSurfaceContainerHigh, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                        val animatedPercent by animateIntAsState(targetValue = (storagePercent * 100).toInt(), animationSpec = tween(800), label = "storagePercentAnim")
                        Text("$animatedPercent%", color = MdOnBg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardItem(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = MdOnBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            content()
        }
    }
}

// ==========================================
// 4. DEVICE INFO LENGKAP - MD3
// ==========================================
@Composable
fun DeviceInfoCompleteCard(brand: String, model: String, socName: String, androidVersion: String, uptime: String, deepSleep: String, kyroosVersion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Device Specs", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MdPrimaryContainer) {
                    Text("Kyroos v$kyroosVersion", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MdOnPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Text(brand.take(2).uppercase(), color = MdPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$brand $model", color = MdOnBg, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(socName, color = MdOutline, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(androidVersion, color = MdOutline, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DeviceStatItem(icon = Icons.Rounded.Schedule, label = "Uptime", value = uptime)
                DeviceStatItem(icon = Icons.Rounded.Bedtime, label = "Deep Sleep", value = deepSleep)
            }
        }
    }
}

@Composable
fun DeviceStatItem(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MdOutline, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, color = MdOutline, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(value, color = MdOnBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
// ==========================================
// 5. KARTU DASHBOARD KECIL & DIALOG PROFIL
// ==========================================
@Composable
fun DashboardGridCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(110.dp).bounceClick(onClick), 
        shape = RoundedCornerShape(20.dp), 
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            // 🔥 FIX MELAR: Ditambah fillMaxWidth() & maxLines supaya teks panjang otomatis terpotong rapi!
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MdOnBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 10.sp, color = MdOutline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ProfileSelectorDialog(currentProfile: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val profiles = listOf(
        Triple("Balanced", "Default optimized balance", Icons.Rounded.Balance), 
        Triple("Powersave", "Prioritize battery savings", Icons.Rounded.BatteryChargingFull), 
        Triple("Gaming", "Higher clocks for gaming", Icons.Rounded.Gamepad), 
        Triple("Extreme", "Unlock maximum performance", Icons.Rounded.Bolt)
    )
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(), // 🔥 Anti melar ke bawah
            shape = RoundedCornerShape(28.dp), 
            colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(24.dp).wrapContentHeight()) {
                Text("Select System Profile", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(20.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.wrapContentHeight()) {
                    profiles.forEach { (name, desc, icon) ->
                        val isSel = currentProfile.lowercase() == name.lowercase()
                        Surface(
                            onClick = { onSelect(name.lowercase()) }, 
                            color = if (isSel) MdPrimaryContainer else MdSurfaceContainer, 
                            shape = RoundedCornerShape(18.dp), 
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (isSel) MdPrimary else MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = if (isSel) MdOnPrimary else MdOutline, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) { 
                                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSel) MdOnPrimaryContainer else MdOnBg)
                                    Text(desc, fontSize = 12.sp, color = if (isSel) MdOnPrimaryContainer.copy(alpha = 0.8f) else MdOutline, lineHeight = 16.sp)
                                }
                                if (isSel) Icon(Icons.Rounded.CheckCircle, null, tint = MdPrimary, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { 
                    TextButton(onClick = onDismiss) { Text("Cancel", fontWeight = FontWeight.Bold, color = MdOutline) } 
                }
            }
        }
    }
}

// ==========================================
// 6. APP LIST & FILTERING (MD3)
// ==========================================
@Composable
fun AppIconView(packageName: String, modifier: Modifier = Modifier) {
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                withContext(Dispatchers.Main) { iconBitmap = drawable.toImageBitmap() }
            } catch (e: Exception) { }
        }
    }
    Crossfade(targetState = iconBitmap, animationSpec = tween(400), label = "icon") { bitmap ->
        if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Box(modifier = modifier.fillMaxSize().background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Android, null, tint = MdOutlineVariant) }
    }
}

@Composable
fun FilterableAppList(searchQuery: String, onSearchChange: (String) -> Unit, onAppClick: (AppItem) -> Unit) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(AppCache.cachedApps.isEmpty()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (AppCache.cachedApps.isEmpty()) {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(0).map { info ->
                    AppItem(
                        name = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) info.category == ApplicationInfo.CATEGORY_GAME else false
                    )
                }.sortedBy { it.name.lowercase() }
                withContext(Dispatchers.Main) { AppCache.cachedApps = apps; isLoading = false }
            }
        }
    }

    val filteredApps = remember(searchQuery, selectedCategory, AppCache.cachedApps) {
        AppCache.cachedApps.filter { app ->
            val matchesSearch = app.name.contains(searchQuery, true) || app.packageName.contains(searchQuery, true)
            val matchesCat = when(selectedCategory) { "Game" -> app.isGame; "User" -> !app.isSystem; "System" -> app.isSystem; else -> true }
            matchesSearch && matchesCat
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps...", color = MdOutline) }, shape = CircleShape, leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MdPrimary) },
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MdSurfaceContainer, unfocusedContainerColor = MdSurfaceContainer, focusedBorderColor = MdPrimary, unfocusedBorderColor = Color.Transparent), singleLine = true
        )
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "Game", "User", "System")) { cat ->
                val isSel = selectedCategory == cat
                Surface(onClick = { selectedCategory = cat }, shape = CircleShape, color = if(isSel) MdPrimaryContainer else MdSurfaceContainer) {
                    Text(cat, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if(isSel) MdOnPrimaryContainer else MdOutline)
                }
            }
        }
        if (isLoading) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MdPrimary) }
        else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Card(modifier = Modifier.fillMaxWidth().bounceClick { onAppClick(app) }, colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) { AppIconView(app.packageName) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MdOnBg, fontSize = 15.sp)
                                Text(app.packageName, fontSize = 11.sp, color = MdOutline, maxLines = 1)
                            }
                            if (app.isGame) Icon(Icons.Rounded.SportsEsports, null, tint = MdPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppsScreen(onAppClick: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    FilterableAppList(searchQuery = searchQuery, onSearchChange = { searchQuery = it }, onAppClick = { onAppClick(it.packageName) })
}

// ==========================================
// 7. EXTRA TWEAKS SCREEN
// ==========================================
@Composable
fun TweaksScreen(scriptDirPath: String, onNavigateOpaps: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentRenderer by remember { mutableStateOf("Checking...") }
    var showRendererDialog by remember { mutableStateOf(false) }
    var customRes by remember { mutableStateOf(false) }
    var resW by remember { mutableStateOf("") }
    var resH by remember { mutableStateOf("") }
    var cacheConfig by remember { mutableStateOf(false) }
    var cacheLimitMB by remember { mutableStateOf(32f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val prop = ShellUtils.execShizuku("getprop debug.hwui.renderer").trim()
            withContext(Dispatchers.Main) { currentRenderer = prop.ifEmpty { "Default" } }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Text("Extra Optimizations", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MdOnBg, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))

        // HWUI Renderer
        Card(modifier = Modifier.fillMaxWidth().bounceClick { showRendererDialog = true }, colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Renderer Selection", color = MdOnBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Current: $currentRenderer", color = MdOutline, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MdOutline)
            }
        }

        // Custom Resolution & Cache
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FitScreen, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(22.dp)) }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) { Text("Custom Resolution", color = MdOnBg, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Modify screen size", color = MdOutline, fontSize = 12.sp) }
                    Switch(checked = customRes, onCheckedChange = { customRes = it }, colors = SwitchDefaults.colors(checkedThumbColor = MdOnPrimaryContainer, checkedTrackColor = MdPrimary, uncheckedThumbColor = MdOutline, uncheckedTrackColor = MdSurfaceContainerHigh))
                }
                AnimatedVisibility(visible = customRes) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = resW, onValueChange = { resW = it }, placeholder = { Text("Width") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
                            OutlinedTextField(value = resH, onValueChange = { resH = it }, placeholder = { Text("Height") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { scope.launch(Dispatchers.IO) { ShellUtils.execShizuku("wm size ${resW}x${resH}") } }, modifier = Modifier.weight(1f)) { Text("Apply") }
                            Button(onClick = { scope.launch(Dispatchers.IO) { ShellUtils.execShizuku("wm size reset") } }, modifier = Modifier.weight(1f)) { Text("Reset") }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().bounceClick { onNavigateOpaps() }, colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GppBad, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) { Text("Brutal AppOps", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Aggressive app restriction", color = MdOutline, fontSize = 12.sp) }
                Icon(Icons.Rounded.ChevronRight, null, tint = MdOutline)
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showRendererDialog) {
        Dialog(onDismissRequest = { showRendererDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp).wrapContentHeight()) {
                    Text("Select HWUI Renderer", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.wrapContentHeight()) {
                        listOf(Triple("skiagl", "SkiaGL", "Stable OpenGL rendering"), Triple("skiavk", "SkiaVK", "Vulkan for better FPS"), Triple("default", "Default", "System default")).forEach { (key, name, desc) ->
                            val isSel = currentRenderer.contains(key, true) || (key == "default" && (currentRenderer == "Default" || currentRenderer.isEmpty()))
                            Surface(
                                onClick = { scope.launch(Dispatchers.IO) { ShellUtils.execShizuku(if (key == "default") "setprop debug.hwui.renderer \"\"" else "setprop debug.hwui.renderer $key"); val newProp = ShellUtils.execShizuku("getprop debug.hwui.renderer").trim(); withContext(Dispatchers.Main) { currentRenderer = newProp.ifEmpty { "Default" }; showRendererDialog = false } } }, 
                                shape = RoundedCornerShape(18.dp), color = if (isSel) MdPrimaryContainer else MdSurfaceContainer, modifier = Modifier.fillMaxWidth().wrapContentHeight()
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(if (isSel) MdPrimary else MdSurfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = if (isSel) MdOnPrimary else MdOutline, modifier = Modifier.size(22.dp)) }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) { Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSel) MdOnPrimaryContainer else MdOnBg); Text(desc, fontSize = 12.sp, color = if (isSel) MdOnPrimaryContainer.copy(alpha = 0.8f) else MdOutline, lineHeight = 16.sp) }
                                    if(isSel) Icon(Icons.Rounded.CheckCircle, null, tint = MdPrimary, modifier = Modifier.size(26.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { showRendererDialog = false }) { Text("Cancel", fontWeight = FontWeight.Bold, color = MdOutline) } }
                }
            }
        }
    }
}

// ==========================================
// 8. APP DETAIL SCREEN (DRIVER, COMPILER, BATTERY)
// ==========================================
@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scriptDirPath = File(context.getExternalFilesDir(null), "scripts").absolutePath
    val scope = rememberCoroutineScope()
    var angleDriver by remember { mutableStateOf(false) }
    var gameDriver by remember { mutableStateOf(false) }
    var devDriver by remember { mutableStateOf(false) }
    var whitelist by remember { mutableStateOf(false) }
    var showCompilerDialog by remember { mutableStateOf(false) }
    var appName by remember { mutableStateOf("") }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try { appName = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() } catch (e: Exception) { appName = packageName }
            val conf = readKyroosConfig(scriptDirPath)
            angleDriver = conf["angle_apps"]?.split(",")?.contains(packageName) ?: false
            gameDriver = conf["game_apps"]?.split(",")?.contains(packageName) ?: false
            devDriver = conf["dev_apps"]?.split(",")?.contains(packageName) ?: false
            whitelist = conf["whitelist_apps"]?.split(",")?.contains(packageName) ?: false
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) { AppIconView(packageName, Modifier.size(48.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName, color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(packageName, color = MdOutline, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Memory, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(18.dp)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Graphics Driver", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                DriverOptionRow("Angle Driver", "Force OpenGL ES rendering", angleDriver) { angleDriver = it; scope.launch(Dispatchers.IO) { val list = updateAppListInConfig(scriptDirPath, "angle_apps", packageName, it); ShellUtils.execShizuku(if(list.isNotEmpty()) "settings put global angle_gl_driver_selection_pkgs $list" else "settings delete global angle_gl_driver_selection_pkgs") } }
                DriverOptionRow("Game Driver", "Game overlay optimizations", gameDriver) { gameDriver = it; if(it) devDriver = false; scope.launch(Dispatchers.IO) { val list = updateAppListInConfig(scriptDirPath, "game_apps", packageName, it); updateAppListInConfig(scriptDirPath, "dev_apps", packageName, false); ShellUtils.execShizuku(if(list.isNotEmpty()) "settings put global game_driver_opt_in_apps $list" else "settings delete global game_driver_opt_in_apps") } }
                DriverOptionRow("Developer Driver", "Prerelease GPU drivers", devDriver) { devDriver = it; if(it) gameDriver = false; scope.launch(Dispatchers.IO) { val list = updateAppListInConfig(scriptDirPath, "dev_apps", packageName, it); updateAppListInConfig(scriptDirPath, "game_apps", packageName, false); ShellUtils.execShizuku(if(list.isNotEmpty()) "settings put global game_driver_prerelease_opt_in_apps $list" else "settings delete global game_driver_prerelease_opt_in_apps") } }
            }
        }
        
        // 🔥 FIX COMPILER CARD: Sekarang hanya tombol aksi eksekusi!
        Card(modifier = Modifier.fillMaxWidth().bounceClick { showCompilerDialog = true }, colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Code, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) { 
                    Text("ART Compiler", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Optimize app performance", color = MdOutline, fontSize = 12.sp) 
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MdOutline)
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.BatterySaver, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) { Text("Battery Whitelist", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("Exclude from doze mode", color = MdOutline, fontSize = 12.sp) }
                Switch(checked = whitelist, onCheckedChange = { whitelist = it; scope.launch(Dispatchers.IO) { updateAppListInConfig(scriptDirPath, "whitelist_apps", packageName, it); ShellUtils.execShizuku(if(it) "cmd deviceidle whitelist +$packageName" else "cmd deviceidle whitelist -$packageName") } }, colors = SwitchDefaults.colors(checkedThumbColor = MdOnPrimaryContainer, checkedTrackColor = MdPrimary, uncheckedThumbColor = MdOutline, uncheckedTrackColor = MdSurfaceContainerHigh))
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    // 🔥 FIX COMPILER DIALOG: Hanya eksekusi perintah (Action List), tidak ada status/ceklis!
    if (showCompilerDialog) {
        val compilers = listOf(
            Triple("Speed-profile", "Profile-guided optimization", Icons.Rounded.Analytics), 
            Triple("Speed", "Maximum performance", Icons.Rounded.Speed), 
            Triple("Space", "Optimize for storage", Icons.Rounded.Storage)
        )
        Dialog(onDismissRequest = { showCompilerDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(), 
                shape = RoundedCornerShape(28.dp), 
                colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(24.dp).wrapContentHeight()) {
                    Text("Execute Compiler", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.wrapContentHeight()) {
                        compilers.forEach { (name, desc, icon) ->
                            Surface(
                                onClick = { 
                                    showCompilerDialog = false
                                    Toast.makeText(context, "Compiling with $name...", Toast.LENGTH_SHORT).show()
                                    scope.launch(Dispatchers.IO) { 
                                        val filter = name.lowercase()
                                        ShellUtils.execShizuku("cmd package compile -m $filter -f $packageName")
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Compiler applied: $name", Toast.LENGTH_SHORT).show() } 
                                    } 
                                },
                                shape = RoundedCornerShape(18.dp), 
                                color = MdSurfaceContainer, // 🔥 Warna netral karena ini adalah tombol eksekusi
                                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                                        Icon(icon, null, tint = MdOutline, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) { 
                                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MdOnBg)
                                        Text(desc, fontSize = 12.sp, color = MdOutline, lineHeight = 16.sp) 
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { 
                        TextButton(onClick = { showCompilerDialog = false }) { Text("Cancel", fontWeight = FontWeight.Bold, color = MdOutline) } 
                    }
                }
            }
        }
    }
}

@Composable
fun DriverOptionRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(16.dp), color = if (checked) MdPrimaryContainer.copy(alpha = 0.4f) else Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = if (checked) MdOnPrimaryContainer else MdOnBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (checked) MdOnPrimaryContainer.copy(alpha = 0.8f) else MdOutline, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = MdOnPrimaryContainer, checkedTrackColor = MdPrimary, uncheckedThumbColor = MdOutline, uncheckedTrackColor = MdSurfaceContainerHigh))
        }
    }
}

// ==========================================
// 9. APPOPS & DEVELOPER PROFILE
// ==========================================
@Composable
fun OpapsScreen(scriptDirPath: String) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GppBad, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.width(14.dp))
                Column { Text("Brutal AppOps", color = MdOnBg, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Tap app to restrict background activity", color = MdOutline, fontSize = 12.sp) }
            }
        }
        FilterableAppList(query, { query = it }, { app -> 
            Toast.makeText(context, "Restricting ${app.name}...", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) { 
                ShellUtils.execShizuku("for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND WAKE_LOCK START_FOREGROUND; do appops set ${app.packageName} \$op ignore; done")
                withContext(Dispatchers.Main) { Toast.makeText(context, "${app.name} restricted!", Toast.LENGTH_SHORT).show() }
            } 
        })
    }
}

@Composable
fun DevProfileModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (AppCache.githubAvatar == null) {
            withContext(Dispatchers.IO) { try { AppCache.githubAvatar = BitmapFactory.decodeStream(URL("https://github.com/andiisking.png").openConnection().getInputStream()).asImageBitmap() } catch (e: Exception) { } }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
            Column(modifier = Modifier.padding(24.dp).wrapContentHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                    if (AppCache.githubAvatar != null) Image(bitmap = AppCache.githubAvatar!!, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.Person, null, tint = MdOnPrimaryContainer, modifier = Modifier.size(40.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("KonekoDev", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MdOnBg)
                Text("Lead Developer KyrooS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MdPrimary)
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SocialButton(Icons.Rounded.Code, { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/andiisking"))) }, Color(0xFF24292E))
                    SocialButton(Icons.Rounded.Send, { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Koneko_dev"))) }, Color(0xFF2AABEE))
                    SocialButton(Icons.Rounded.Campaign, { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Droid_ch"))) }, Color(0xFF0088CC))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MdSurfaceContainer, contentColor = MdOnBg)) { Text("Close", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SocialButton(icon: ImageVector, onClick: () -> Unit, color: Color) {
    Surface(onClick = onClick, modifier = Modifier.size(50.dp), shape = RoundedCornerShape(14.dp), color = color) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
    }
}
