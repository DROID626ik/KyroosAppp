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
package com.kyroos.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MdBg = Color(0xFF1A120E)
val MdOnBg = Color(0xFFEFE0DA)
val MdSurfaceContainer = Color(0xFF271E1A)
val MdSurfaceContainerHigh = Color(0xFF322824)
val MdPrimary = Color(0xFFFFB596)
val MdOnPrimary = Color(0xFF5A1C00)
val MdPrimaryContainer = Color(0xFF723523)
val MdOnPrimaryContainer = Color(0xFFFFDBD1)
val MdSecondaryContainer = Color(0xFF534339)
val MdOnSecondaryContainer = Color(0xFFFFDBCA)
val MdOutline = Color(0xFFA08D85)
val MdOutlineVariant = Color(0xFF53433F)

val KyroosColorScheme = darkColorScheme(
    background = MdBg, 
    onBackground = MdOnBg,
    surface = MdBg, 
    onSurface = MdOnBg,
    surfaceVariant = MdSurfaceContainer,
    onSurfaceVariant = MdOutline, 
    primary = MdPrimary,
    onPrimary = MdOnPrimary,
    primaryContainer = MdPrimaryContainer, 
    onPrimaryContainer = MdOnPrimaryContainer,
    secondaryContainer = MdSecondaryContainer, 
    onSecondaryContainer = MdOnSecondaryContainer,
    outline = MdOutline,
    outlineVariant = MdOutlineVariant
)

@Composable
fun KyroosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KyroosColorScheme, content = content)
}
