package com.kyroos.app.ui.screens

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import com.kyroos.app.ui.components.bounceClick
import com.kyroos.app.ui.theme.*
import com.kyroos.app.utils.ShellUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun getKasanePathForGame(context: Context): String {
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
fun GameSpaceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localGames by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoadingGames by remember { mutableStateOf(true) }
    
    var selectedGame by remember { mutableStateOf<AppItem?>(null) }
    
    var gameFps by remember { mutableStateOf("") }
    var gameScale by remember { mutableStateOf("") }
    var activeGameMode by remember { mutableStateOf("") }

    var isBoosting by remember { mutableStateOf(false) }
    var boostText by remember { mutableStateOf("Initializing...") }

    val listState = rememberLazyListState()
    val kasanePath = remember { getKasanePathForGame(context) } // Pakai fungsi baru

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            
            val games = packages.filter { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    @Suppress("DEPRECATION")
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                }
            }.map { info ->
                val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                AppItem(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    isSystem = isSys,
                    isGame = true
                )
            }.sortedBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                localGames = games
                isLoadingGames = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Game Space", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MdOnBg)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Select and optimize your games", fontSize = 13.sp, color = MdOutline, fontWeight = FontWeight.Medium)
                }
                Box(modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MdPrimaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.VideogameAsset, contentDescription = null, tint = MdOnPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (isLoadingGames) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MdPrimary, modifier = Modifier.size(40.dp))
            }
        } else if (localGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.VideogameAssetOff, contentDescription = null, tint = MdOutline, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Games Detected", color = MdOutline, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyRow(
                state = listState, 
                modifier = Modifier.fillMaxWidth(), 
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), 
                horizontalArrangement = Arrangement.spacedBy(14.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(localGames, key = { it.packageName }) { game ->
                    val isSelected = selectedGame == game
                    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f), label = "cardScale")
                    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0.85f, animationSpec = tween(300), label = "cardAlpha")
                    val bgColor by animateColorAsState(targetValue = if (isSelected) MdPrimaryContainer else MdSurfaceContainer, animationSpec = tween(300), label = "cardBg")
                    val textColor by animateColorAsState(targetValue = if (isSelected) MdOnPrimaryContainer else MdOnBg, animationSpec = tween(300), label = "cardText")

                    Card(modifier = Modifier.width(105.dp).height(130.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }.bounceClick { selectedGame = game }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = bgColor)) {
                        Column(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) { 
                                AppIconView(packageName = game.packageName, modifier = Modifier.fillMaxSize().padding(6.dp)) 
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(game.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(MdPrimary))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = selectedGame != null, 
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 200f)) + fadeIn(), 
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    Text("Action Panel", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MdOnBg, modifier = Modifier.padding(top = 10.dp))

                    Button(onClick = {
                        isBoosting = true
                        scope.launch(Dispatchers.IO) {
                            val pkg = selectedGame!!.packageName
                            val kasaneTmp = "/data/local/tmp/kasane" // Target bypass
                            
                            val script = """
                                am kill-all
                                for a in --reset --disable --disable-detailed-tracking; do dumpsys binder_calls_stats ${'$'}a; done
                                for b in --clear --stop-testing; do dumpsys procstats ${'$'}b; done
                                for c in ab-logging-disable dwb-logging-disable dmd-logging-disable; do cmd display ${'$'}c; done
                                
                                # EKSEKUSI KASANE VIA TMP AGAR TIDAK DIBLOKIR ANDROID
                                cp $kasanePath $kasaneTmp
                                chmod 777 $kasaneTmp
                                $kasaneTmp -a $pkg -m d
                                
                                monkey -p $pkg -c android.intent.category.LAUNCHER 1
                            """.trimIndent()
                            
                            delay(2500) 
                            ShellUtils.execShizuku(script)
                            
                            withContext(Dispatchers.Main) { 
                                isBoosting = false
                                Toast.makeText(context, "${selectedGame!!.name} Accelerated!", Toast.LENGTH_SHORT).show() 
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = MdPrimary, contentColor = MdOnPrimary)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("BOOST & PLAY", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Engine Interventions", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MdOnBg)

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = gameFps, onValueChange = { gameFps = it }, placeholder = { Text("e.g. 90") }, label = { Text("Force FPS") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MdPrimary, unfocusedBorderColor = MdOutlineVariant))
                                OutlinedTextField(value = gameScale, onValueChange = { gameScale = it }, placeholder = { Text("0.5 - 0.9") }, label = { Text("Downscale") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp)) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MdPrimary, unfocusedBorderColor = MdOutlineVariant))
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isPerf = activeGameMode == "performance"
                        val isBatt = activeGameMode == "battery"
                        
                        Card(modifier = Modifier.weight(1f).height(70.dp).bounceClick {
                            activeGameMode = "performance"
                            scope.launch(Dispatchers.IO) {
                                ShellUtils.execShizuku("cmd game mode performance ${selectedGame!!.packageName}")
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Performance Mode Active!", Toast.LENGTH_SHORT).show() }
                            }
                        }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if(isPerf) MdPrimaryContainer else MdSurfaceContainer)) {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Rounded.RocketLaunch, contentDescription = null, tint = if(isPerf) MdOnPrimaryContainer else MdOutline, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Performance", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if(isPerf) MdOnPrimaryContainer else MdOutline)
                            }
                        }

                        Card(modifier = Modifier.weight(1f).height(70.dp).bounceClick {
                            activeGameMode = "battery"
                            scope.launch(Dispatchers.IO) {
                                ShellUtils.execShizuku("cmd game mode battery ${selectedGame!!.packageName}")
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Battery Mode Active!", Toast.LENGTH_SHORT).show() }
                            }
                        }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if(isBatt) MdPrimaryContainer else MdSurfaceContainer)) {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Rounded.BatterySaver, contentDescription = null, tint = if(isBatt) MdOnPrimaryContainer else MdOutline, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Battery", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if(isBatt) MdOnPrimaryContainer else MdOutline)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            activeGameMode = ""
                            scope.launch(Dispatchers.IO) {
                                ShellUtils.execShizuku("cmd game reset ${selectedGame!!.packageName}")
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Engine Defaulted!", Toast.LENGTH_SHORT).show() }
                            }
                        }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                            Text("Reset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(onClick = {
                            activeGameMode = "custom"
                            scope.launch(Dispatchers.IO) {
                                val fps = if (gameFps.isNotBlank()) "--fps $gameFps" else ""
                                val scale = if (gameScale.isNotBlank()) "--downscale $gameScale" else ""
                                ShellUtils.execShizuku("cmd game mode custom ${selectedGame!!.packageName}")
                                if (fps.isNotEmpty() || scale.isNotEmpty()) {
                                    ShellUtils.execShizuku("cmd game set $fps $scale ${selectedGame!!.packageName}")
                                }
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Config Applied!", Toast.LENGTH_SHORT).show() }
                            }
                        }, modifier = Modifier.weight(2f).height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MdPrimary, contentColor = MdOnPrimary)) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Apply Config", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (isBoosting) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alphaPulse by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alphaPulse"
        )

        LaunchedEffect(Unit) {
            boostText = "Killing background apps..."
            delay(600)
            boostText = "Flushing telemetry logs..."
            delay(600)
            boostText = "Loading Kasane Fadvise Data..."
            delay(600)
            boostText = "Deep Injecting D-mode..."
        }

        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh),
                    modifier = Modifier.widthIn(min = 280.dp, max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MdPrimary.copy(alpha = 0.2f), strokeWidth = 4.dp, modifier = Modifier.size(64.dp))
                            CircularProgressIndicator(color = MdPrimary, strokeWidth = 4.dp, modifier = Modifier.size(64.dp).graphicsLayer { alpha = alphaPulse })
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                        AnimatedContent(
                            targetState = boostText,
                            transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) },
                            label = "boost_text"
                        ) { targetText ->
                            Text(
                                text = targetText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MdOnBg,
                                textAlign = TextAlign.Center,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
