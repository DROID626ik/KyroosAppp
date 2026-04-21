package com.kyroos.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyroos.app.ui.theme.*
import com.kyroos.app.utils.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

@Composable
fun FileManagerScreen(
    rootPath: String, 
    currentPath: String, 
    onPathChange: (String) -> Unit, 
    onOpenFileEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDir = File(currentPath)
    
    var files by remember { mutableStateOf(listOf<File>()) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") } 

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPropertiesDialog by remember { mutableStateOf(false) } 
    var createMode by remember { mutableStateOf("File") } 
    
    var showOutputDialog by remember { mutableStateOf(false) }
    var scriptOutput by remember { mutableStateOf("") }
    
    var showPathJumpDialog by remember { mutableStateOf(false) }
    
    var clipboardFile by remember { mutableStateOf<File?>(null) }
    var clipboardIsMove by remember { mutableStateOf(false) }

    BackHandler(enabled = currentDir.absolutePath != rootPath && currentDir.parentFile != null) {
        onPathChange(currentDir.parentFile!!.absolutePath)
        searchQuery = ""
    }

    LaunchedEffect(currentPath, refreshTrigger) {
        withContext(Dispatchers.IO) {
            val fileList = currentDir.listFiles()?.toList() ?: emptyList()
            val sortedFiles = fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            withContext(Dispatchers.Main) { files = sortedFiles }
        }
    }

    val displayedFiles = if (searchQuery.isBlank()) files else files.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = { createMode = "File"; showCreateDialog = true }, containerColor = MdPrimary, contentColor = MdOnPrimary, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Add, "New")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MdSurfaceContainer)
                    .clickable { showPathJumpDialog = true }
                    .padding(12.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = MdPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(currentDir.absolutePath, color = MdOnBg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Refresh, null, tint = MdOutline, modifier = Modifier.size(20.dp).clickable { refreshTrigger++ })
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search in this folder...", color = MdOutlineVariant, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MdOutline) },
                trailingIcon = { if (searchQuery.isNotEmpty()) Icon(Icons.Rounded.Clear, null, tint = MdOutline, modifier = Modifier.clickable { searchQuery = "" }) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MdSurfaceContainerHigh, focusedBorderColor = MdPrimary)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (clipboardFile != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = MdPrimaryContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (clipboardIsMove) "Moving: ${clipboardFile!!.name}" else "Copying: ${clipboardFile!!.name}", color = MdOnPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row {
                            TextButton(onClick = { clipboardFile = null }) { Text("Cancel", color = MdOnPrimaryContainer) }
                            Button(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val dest = File(currentDir, clipboardFile!!.name).absolutePath
                                    val src = clipboardFile!!.absolutePath
                                    val cmd = if (clipboardIsMove) "mv \"$src\" \"$dest\"" else "cp -r \"$src\" \"$dest\""
                                    ShellUtils.execShizuku(cmd)
                                    withContext(Dispatchers.Main) { clipboardFile = null; refreshTrigger++; Toast.makeText(context, "Pasted via Root", Toast.LENGTH_SHORT).show() }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = MdPrimary, contentColor = MdOnPrimary)) { Text("Paste") }
                        }
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                if (currentDir.absolutePath != rootPath && currentDir.parentFile != null && searchQuery.isBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().clickable { onPathChange(currentDir.parentFile!!.absolutePath) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.TurnLeft, null, tint = MdPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("[ .. ]  Go Up", fontWeight = FontWeight.Bold, color = MdOnBg)
                            }
                        }
                    }
                }

                if (displayedFiles.isEmpty()) { 
                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text(if(searchQuery.isNotBlank()) "No files found" else "Folder is empty or requires Root", color = MdOutline) } } 
                }

                items(displayedFiles) { file ->
                    val isDir = file.isDirectory
                    val isZip = file.name.endsWith(".zip", true)
                    val isSh = file.name.endsWith(".sh", true)
                    val isApk = file.name.endsWith(".apk", true)
                    
                    val icon = if (isDir) Icons.Rounded.Folder else if (isZip) Icons.Rounded.FolderZip else if (isSh) Icons.Rounded.Terminal else if (isApk) Icons.Rounded.Android else Icons.Rounded.Description
                    val iconColor = if (isDir || isZip || isSh || isApk) MdPrimary else MdOutline
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { if (isDir) { onPathChange(file.absolutePath); searchQuery = "" } else { selectedFile = file; showOptionsDialog = true } }.padding(horizontal = 2.dp),
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer)
                    ) {
                        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MdOnBg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sdf = SimpleDateFormat("dd MMM yy", Locale.getDefault())
                                Text(if (isDir) "Folder • ${sdf.format(Date(file.lastModified()))}" else "${file.length() / 1024} KB • ${sdf.format(Date(file.lastModified()))}", fontSize = 11.sp, color = MdOutline)
                            }
                            Icon(Icons.Rounded.MoreVert, null, tint = MdOutline, modifier = Modifier.size(20.dp).clickable { selectedFile = file; showOptionsDialog = true })
                        }
                    }
                }
            }
        }
    }

    if (showPathJumpDialog) {
        var inputPath by remember { mutableStateOf(currentDir.absolutePath) }
        Dialog(onDismissRequest = { showPathJumpDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Jump to Path", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputPath, 
                        onValueChange = { inputPath = it }, 
                        label = { Text("Directory Path") }, 
                        modifier = Modifier.fillMaxWidth(), 
                        shape = RoundedCornerShape(12.dp), 
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { 
                        TextButton(onClick = { showPathJumpDialog = false }) { Text("Cancel", color = MdOutline) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (inputPath.isNotBlank()) {
                                onPathChange(inputPath)
                                showPathJumpDialog = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MdPrimary)) { Text("Go") }
                    }
                }
            }
        }
    }

    if (showOptionsDialog && selectedFile != null) {
        val file = selectedFile!!
        val isDir = file.isDirectory
        val isZip = file.name.endsWith(".zip", true)
        val isSh = file.name.endsWith(".sh", true)
        val isApk = file.name.endsWith(".apk", true)
        
        Dialog(onDismissRequest = { showOptionsDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.8f)) {
                    Text(file.name, color = MdOnBg, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isSh && !isDir) {
                            FileOptionItem("Run Script", Icons.Rounded.PlayArrow, MdPrimary) {
                                showOptionsDialog = false; Toast.makeText(context, "Running...", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    val result = ShellUtils.execShizuku("sh \"${file.absolutePath}\"")
                                    withContext(Dispatchers.Main) { scriptOutput = result.ifEmpty { "Success: No output returned." }; showOutputDialog = true }
                                }
                            }
                        }
                        if (isApk && !isDir) {
                            FileOptionItem("Install APK (Silent Root)", Icons.Rounded.SystemUpdate, MdPrimary) {
                                showOptionsDialog = false; Toast.makeText(context, "Installing via Shizuku...", Toast.LENGTH_LONG).show()
                                scope.launch(Dispatchers.IO) {
                                    val result = ShellUtils.execShizuku("pm install -r \"${file.absolutePath}\"")
                                    withContext(Dispatchers.Main) { scriptOutput = result.ifEmpty { "Success" }; showOutputDialog = true }
                                }
                            }
                        }
                        if (isZip) {
                            FileOptionItem("Extract ZIP Here", Icons.Rounded.Unarchive, MdPrimary) {
                                showOptionsDialog = false; Toast.makeText(context, "Extracting...", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    ShellUtils.execShizuku("unzip -o \"${file.absolutePath}\" -d \"${currentDir.absolutePath}\"")
                                    withContext(Dispatchers.Main) { refreshTrigger++; Toast.makeText(context, "Extracted", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                        
                        Divider(color = MdSurfaceContainer, modifier = Modifier.padding(vertical = 4.dp))
                        
                        if (!isDir && !isZip && !isApk) { FileOptionItem("Edit File", Icons.Rounded.Edit, MdOnBg) { showOptionsDialog = false; onOpenFileEditor(file.absolutePath) } }
                        if (isDir || !isZip) {
                            FileOptionItem("Compress to ZIP", Icons.Rounded.Archive, MdOnBg) {
                                showOptionsDialog = false; Toast.makeText(context, "Zipping...", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) {
                                    ShellUtils.execShizuku("cd \"${currentDir.absolutePath}\" && zip -r \"${file.name}.zip\" \"${file.name}\"")
                                    withContext(Dispatchers.Main) { refreshTrigger++; Toast.makeText(context, "Zipped", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                        
                        FileOptionItem("Copy", Icons.Rounded.ContentCopy, MdOnBg) { clipboardFile = file; clipboardIsMove = false; showOptionsDialog = false }
                        FileOptionItem("Move (Cut)", Icons.Rounded.DriveFileMove, MdOnBg) { clipboardFile = file; clipboardIsMove = true; showOptionsDialog = false }
                        FileOptionItem("Rename", Icons.Rounded.DriveFileRenameOutline, MdOnBg) { showOptionsDialog = false; showRenameDialog = true }
                        
                        Divider(color = MdSurfaceContainer, modifier = Modifier.padding(vertical = 4.dp))
                        
                        FileOptionItem("Properties", Icons.Rounded.Info, MdOutline) { showOptionsDialog = false; showPropertiesDialog = true }
                        FileOptionItem("Delete", Icons.Rounded.Delete, MaterialTheme.colorScheme.error) { 
                            showOptionsDialog = false
                            scope.launch(Dispatchers.IO) { ShellUtils.execShizuku("rm -rf \"${file.absolutePath}\""); withContext(Dispatchers.Main){ refreshTrigger++ } }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { showOptionsDialog = false }) { Text("Close", color = MdOutline) } }
                }
            }
        }
    }

    if (showPropertiesDialog && selectedFile != null) {
        val file = selectedFile!!
        Dialog(onDismissRequest = { showPropertiesDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Properties", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())
                    val perms = "${if(file.canRead()) "r" else "-"}${if(file.canWrite()) "w" else "-"}${if(file.canExecute()) "x" else "-"}"
                    
                    Column(modifier = Modifier.background(MdSurfaceContainer, RoundedCornerShape(12.dp)).padding(16.dp).fillMaxWidth()) {
                        Text("Name:", fontSize = 12.sp, color = MdOutline)
                        Text(file.name, fontSize = 14.sp, color = MdOnBg, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Path:", fontSize = 12.sp, color = MdOutline)
                        Text(file.absolutePath, fontSize = 13.sp, color = MdOnBg, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Size:", fontSize = 12.sp, color = MdOutline)
                        Text(if (file.isDirectory) "Directory" else "${file.length()} bytes", fontSize = 14.sp, color = MdOnBg, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Last Modified:", fontSize = 12.sp, color = MdOutline)
                        Text(sdf.format(Date(file.lastModified())), fontSize = 14.sp, color = MdOnBg, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Permissions:", fontSize = 12.sp, color = MdOutline)
                        Text(perms, fontSize = 14.sp, color = MdPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { 
                        TextButton(onClick = { showPropertiesDialog = false }) { Text("Close", color = MdOutline) } 
                    }
                }
            }
        }
    }

    if (showRenameDialog || showCreateDialog) {
        var inputName by remember { mutableStateOf(if (showRenameDialog) selectedFile!!.name else "") }
        Dialog(onDismissRequest = { showRenameDialog = false; showCreateDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (showRenameDialog) "Rename" else "Create New", color = MdOnBg, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (showCreateDialog) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(onClick = { createMode = "File" }, color = if (createMode=="File") MdPrimaryContainer else MdSurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("File", modifier = Modifier.padding(12.dp), color = if (createMode=="File") MdOnPrimaryContainer else MdOnBg, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Surface(onClick = { createMode = "Folder" }, color = if (createMode=="Folder") MdPrimaryContainer else MdSurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Folder", modifier = Modifier.padding(12.dp), color = if (createMode=="Folder") MdOnPrimaryContainer else MdOnBg, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { 
                        TextButton(onClick = { showRenameDialog = false; showCreateDialog = false }) { Text("Cancel", color = MdOutline) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (inputName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val target = File(currentDir, inputName).absolutePath
                                    if (showRenameDialog) ShellUtils.execShizuku("mv \"${selectedFile!!.absolutePath}\" \"$target\"")
                                    else if (createMode == "Folder") ShellUtils.execShizuku("mkdir -p \"$target\"")
                                    else ShellUtils.execShizuku("touch \"$target\"")
                                    withContext(Dispatchers.Main) { refreshTrigger++; showRenameDialog = false; showCreateDialog = false }
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MdPrimary)) { Text("Save") }
                    }
                }
            }
        }
    }

    if (showOutputDialog) {
        Dialog(onDismissRequest = { showOutputDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MdSurfaceContainerHigh)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Terminal, null, tint = MdPrimary); Spacer(modifier = Modifier.width(8.dp))
                        Text("Output", color = MdOnBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).background(MdSurfaceContainer, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text(scriptOutput, color = MdOnBg, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { showOutputDialog = false }) { Text("Close", color = MdOutline) } }
                }
            }
        }
    }
}

@Composable
fun FileOptionItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MdSurfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)); Spacer(modifier = Modifier.width(16.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
