package com.kyroos.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyroos.app.ui.theme.*

// --- CUSTOM MODIFIERS ---
fun Modifier.bounceClick(onClick: () -> Unit): Modifier = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f, 
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f), 
        label = "bounce_scale"
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f, 
        animationSpec = tween(150), 
        label = "bounce_alpha"
    )
    this.graphicsLayer { 
        scaleX = pressScale
        scaleY = pressScale
        alpha = pressAlpha 
    }.clickable(
        interactionSource = interactionSource, 
        indication = null, 
        onClick = onClick
    )
}

// --- SHARED UI WIDGETS (YANG SEMPAT HILANG) ---
@Composable
fun InfoItem(icon: ImageVector, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MdSurfaceContainerHigh), 
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = MdPrimary, modifier = Modifier.size(22.dp)) }
        Spacer(modifier = Modifier.width(16.dp))
        Column { 
            Text(title, fontSize = 11.sp, color = MdOutline, letterSpacing = 0.5.sp)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MdOnBg) 
        }
    }
}

// --- SWITCH THEME ---
@Composable
fun kyroosSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MdOnPrimaryContainer, 
    checkedTrackColor = MdPrimary,
    uncheckedThumbColor = MdOutline, 
    uncheckedTrackColor = MdSurfaceContainerHigh, 
    uncheckedBorderColor = Color.Transparent
)

// --- CONFIG CARDS ---
@Composable
fun ConfigCardSwitch(title: String, subtitle: String, icon: ImageVector, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
            .bounceClick { if (enabled) onCheckedChange(!checked) }, 
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = if (enabled) MdOutline else MdOutlineVariant, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column { 
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (enabled) MdOnBg else MdOutline)
                    Text(subtitle, fontSize = 13.sp, color = if (enabled) MdOutline else MdOutlineVariant) 
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, colors = kyroosSwitchColors())
        }
    }
}

// --- CARD NAV (YANG SEMPAT HILANG) ---
@Composable
fun ConfigCardNav(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).bounceClick { onClick() }, 
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(24.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MdPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column { 
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MdOnBg)
                    Text(subtitle, fontSize = 13.sp, color = MdOutline) 
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MdOutline)
        }
    }
}

// --- EXPANDABLE CARD (YANG BIKIN ERROR PRELOADSCREEN TADI) ---
@Composable
fun ExpandableConfigCard(title: String, subtitle: String, icon: ImageVector, isChecked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)), 
        colors = CardDefaults.cardColors(containerColor = MdSurfaceContainer), shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth().bounceClick { if (enabled) onCheckedChange(!isChecked) }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MdSurfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = if (enabled) MdPrimary else MdOutlineVariant, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column { 
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (enabled) MdOnBg else MdOutline)
                        Text(subtitle, fontSize = 13.sp, color = if (enabled) MdOutline else MdOutlineVariant) 
                    }
                }
                Switch(checked = isChecked, onCheckedChange = onCheckedChange, enabled = enabled, colors = kyroosSwitchColors())
            }
            AnimatedVisibility(
                visible = isChecked && enabled,
                enter = expandVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(350)),
                exit = shrinkVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(350))
            ) { 
                Column { Spacer(modifier = Modifier.height(16.dp)); content() } 
            }
        }
    }
}

// --- BOTTOM NAVIGATION ---
@Composable
fun KyroosBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(MdSurfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(0, selectedTab, Icons.Rounded.Home, "Home", Modifier.weight(1f), onTabSelected)
            NavItem(1, selectedTab, Icons.Rounded.Apps, "Apps", Modifier.weight(1f), onTabSelected)
            NavItem(2, selectedTab, Icons.Rounded.Settings, "Config", Modifier.weight(1f), onTabSelected)
            // 🔥 FIXED: Berubah jadi Files dan Ikon Folder
            NavItem(3, selectedTab, Icons.Rounded.Folder, "Files", Modifier.weight(1f), onTabSelected)
        }
    }
}


@Composable
fun NavItem(index: Int, selectedIndex: Int, icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: (Int) -> Unit) {
    val isSelected = index == selectedIndex
    val iconScale by animateFloatAsState(if (isSelected) 1.2f else 1.0f, spring(0.6f, 250f), label = "nav_scale")
    val bgAlpha by animateFloatAsState(if (isSelected) 1f else 0f, tween(250), label = "nav_bg_alpha")
    val bgScale by animateFloatAsState(if (isSelected) 1f else 0.4f, spring(0.65f, 300f), label = "nav_bg_scale")
    val textOffset by animateDpAsState(if (isSelected) 0.dp else 6.dp, spring(0.7f, 350f), label = "nav_text_offset")
    val textAlpha by animateFloatAsState(if (isSelected) 1f else 0.7f, tween(200), label = "nav_text_alpha")
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.bounceClick { onClick(index) }.padding(vertical = 4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.width(56.dp).height(32.dp).graphicsLayer { scaleX = bgScale; scaleY = bgScale; alpha = bgAlpha }.clip(CircleShape).background(MdPrimaryContainer))
            Icon(icon, null, tint = if (isSelected) MdOnPrimaryContainer else MdOutline, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) MdPrimary else MdOutline, modifier = Modifier.offset(y = textOffset).graphicsLayer { alpha = textAlpha })
    }
}
