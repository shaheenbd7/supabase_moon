package com.moon.supabasetester.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SupabaseGreen = Color(0xFF3ECF8E)
val SupabaseDeep = Color(0xFF006B45)
val SuccessGreen = Color(0xFF2E9E6B)
val ErrorRed = Color(0xFFD64545)

private val Light = lightColorScheme(
    primary = SupabaseDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5D6),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358),
    background = Color(0xFFF6FBF7),
    surface = Color(0xFFF6FBF7),
    surfaceVariant = Color(0xFFDCE5DE),
)

private val Dark = darkColorScheme(
    primary = SupabaseGreen,
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFFB8F5D6),
    secondary = Color(0xFFB2CCBE),
    background = Color(0xFF121614),
    surface = Color(0xFF121614),
    surfaceVariant = Color(0xFF2C352F),
)

@Composable
fun SupabaseMoonTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
