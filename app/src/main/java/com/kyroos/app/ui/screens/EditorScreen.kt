package com.kyroos.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyroos.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(filePath: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = File(filePath)
    
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try { val text = file.readText(); withContext(Dispatchers.Main) { content = text; isLoading = false } } 
            catch (e: Exception) { withContext(Dispatchers.Main) { content = "Error: ${e.message}"; isLoading = false } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column { 
                        Text(file.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MdOnBg) 
                        Text(file.absolutePath, fontSize = 10.sp, color = MdOutline) 
                    } 
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = MdOnBg) } },
                actions = {
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try { file.writeText(content); withContext(Dispatchers.Main) { Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show() } } 
                            catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Failed to save!", Toast.LENGTH_SHORT).show() } }
                        }
                    }) { Icon(Icons.Rounded.Save, "Save", tint = MdPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MdSurfaceContainer)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MdSurfaceContainerHigh)) {
            if (isLoading) CircularProgressIndicator(color = MdPrimary, modifier = Modifier.align(Alignment.Center))
            else {
                BasicTextField(
                    value = content, onValueChange = { content = it },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    textStyle = TextStyle(color = MdOnBg, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                    cursorBrush = SolidColor(MdPrimary)
                )
            }
        }
    }
}
