//
// Copyright 2024 andiisking
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.kyroos.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShellUtils {
    fun createShizukuProcess(cmdArray: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmdArray, null, null) as Process
    }

    suspend fun execShizuku(command: String): String = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) return@withContext "Error: Shizuku not running"
        try {
            val process = createShizukuProcess(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (output.isNotEmpty()) output.trim() else error.trim()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmap = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
        Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
