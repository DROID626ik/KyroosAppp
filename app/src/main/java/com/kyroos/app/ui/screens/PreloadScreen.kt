package com.kyroos.app.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import com.kyroos.app.ui.components.ConfigCardSwitch
import com.kyroos.app.ui.components.ExpandableConfigCard
import com.kyroos.app.ui.components.bounceClick
import com.kyroos.app.ui.theme.*
import com.kyroos.app.utils.ShellUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun getKasanePath(context: Context): String {
    val dir = context.getExternalFilesDir(null)
    val file = File(dir, "kasane")
    try {
        if (!file.exists()) {
            context.assets.open("kasane").use { input -> 
                file.outputStream().use { input.copyTo(it) } 
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return file.absolutePath
}

@Composable
fun PreloadScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kasanePath = remember { getKasanePath(context) }

    var localUserApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            
            val apps = packages.filter { info ->
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && 
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }.map { info ->
                AppItem(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    isSystem = false,
                    isGame = false
                )
            }.sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                localUserApps = apps
                isLoadingApps = false
            }
        }
    }
    
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    var selectedMode by remember { mutableStateOf("d") }
    var threads by remember { mutableFloatStateOf(4f) }
    var launchApp by remember { mutableStateOf(true) }
    var saveLog by remember { mutableStateOf(false) }
    
    var showAdvanced by remember { mutableStateOf(false) }
    var customPath by remember { mutableStateOf("") }
    var customExts by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Target Application (Style seragam dgn Launch App)
        Card(modifier = Modifier.fillMaxWidth().bounceClick { showAppPicker = true }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedApp != null) {
                    AppIconView(packageName = selectedApp!!.packageName, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
                } else {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = MdPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedApp?.name ?: "Target Application", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MdOnBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selectedApp?.packageName ?: "Tap to select user app", fontSize = 13.sp, color = MdOutline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MdOutline)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Preload Mode Selector
        Text("Preload Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MdPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(title = "Normal", desc = "fadvise hint", isSelected = selectedMode == "n", onClick = { selectedMode = "n" }, modifier = Modifier.weight(1f))
            ModeCard(title = "Deep", desc = "fadvise + dlopen", isSelected = selectedMode == "d", onClick = { selectedMode = "d" }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(title = "Extreme", desc = "mmap + pop", isSelected = selectedMode == "x", onClick = { selectedMode = "x" }, modifier = Modifier.weight(1f))
            ModeCard(title = "Recursive", desc = "loop check", isSelected = selectedMode == "r", onClick = { selectedMode = "r" }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Engine Allocation (Style seragam dgn Launch App)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, tint = MdPrimary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Engine Allocation", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MdOnBg)
                        Text("Worker Threads", fontSize = 13.sp, color = MdOutline)
                    }
                    Text("${threads.toInt()} Core", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MdPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(value = threads, onValueChange = { threads = it }, valueRange = 1f..16f, steps = 14, colors = SliderDefaults.colors(thumbColor = MdPrimary, activeTrackColor = MdPrimary))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Switches (Sudah Bawaan Seragam)
        ConfigCardSwitch(title = "Launch Application", subtitle = "Open app automatically (-l)", icon = Icons.Rounded.Launch, checked = launchApp) { launchApp = it }
        Spacer(modifier = Modifier.height(12.dp))
        ConfigCardSwitch(title = "Export Log", subtitle = "Save output to storage (-s)", icon = Icons.Rounded.SaveAs, checked = saveLog) { saveLog = it }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Advanced Targeting (Kembali pakai Expandable bawaan biar seragam)
        ExpandableConfigCard(title = "Advanced Targeting", subtitle = "Custom paths & extensions", icon = Icons.Rounded.FolderOpen, isChecked = showAdvanced, onCheckedChange = { showAdvanced = it }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = customPath, onValueChange = { customPath = it }, 
                    label = { Text("Custom Path (-p)") }, placeholder = { Text("e.g. /system/lib64/") }, 
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = MdPrimary) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MdPrimary, unfocusedBorderColor = MdOutlineVariant)
                )
                OutlinedTextField(
                    value = customExts, onValueChange = { customExts = it }, 
                    label = { Text("Custom Extensions (-f)") }, placeholder = { Text("e.g. .mp4,.ts") }, 
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp), tint = MdPrimary) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MdPrimary, unfocusedBorderColor = MdOutlineVariant)
                )
                OutlinedTextField(
                    value = customName, onValueChange = { customName = it }, 
                    label = { Text("Target Filename (-n)") }, placeholder = { Text("e.g. cache_data") }, 
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MdPrimary) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MdPrimary, unfocusedBorderColor = MdOutlineVariant)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (selectedApp == null && customPath.isEmpty() && customName.isEmpty()) {
                    Toast.makeText(context, "Please select an App or provide a custom target!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                scope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Preloading engine started...", Toast.LENGTH_SHORT).show() }
                    
                    var kasaneArgs = "-m $selectedMode -t ${threads.toInt()}"
                    if (selectedApp != null) kasaneArgs += " -a ${selectedApp!!.packageName}"
                    if (customPath.isNotBlank()) kasaneArgs += " -p $customPath"
                    if (customExts.isNotBlank()) kasaneArgs += " -f $customExts"
                    if (customName.isNotBlank()) kasaneArgs += " -n $customName"
                    if (launchApp) kasaneArgs += " -l"
                    if (saveLog) kasaneArgs += " -s"

                    val kasaneTmp = "/data/local/tmp/kasane"
                    
                    val script = """
                        cp $kasanePath $kasaneTmp
                        chmod 777 $kasaneTmp
                        $kasaneTmp $kasaneArgs
                    """.trimIndent()

                    ShellUtils.execShizuku(script)
                    
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Kasane execution completed!", Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MdPrimary, contentColor = MdOnPrimary)
        ) {
            Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("RUN KASANE ENGINE", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    if (showAppPicker) {
        Dialog(onDismissRequest = { showAppPicker = false }) {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.7f).fillMaxWidth()) {
                    Text("Select Application", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MdOnBg, modifier = Modifier.padding(bottom = 16.dp))
                    
                    if (isLoadingApps) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MdPrimary, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                        }
                    } else if (localUserApps.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No Apps Found", color = MdOutline, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(localUserApps, key = { it.packageName }) { app ->
                                Surface(onClick = { selectedApp = app; showAppPicker = false }, shape = RoundedCornerShape(16.dp), color = MdSurfaceContainer) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        AppIconView(packageName = app.packageName, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(app.name, fontWeight = FontWeight.Bold, color = MdOnBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeCard(title: String, desc: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(68.dp).bounceClick(onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MdPrimaryContainer else MdSurfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MdPrimary else MdOutline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (isSelected) MdOnPrimaryContainer else MdOnBg)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 11.sp, color = if (isSelected) MdOnPrimaryContainer.copy(alpha=0.8f) else MdOutline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 24.dp))
        }
    }
}
