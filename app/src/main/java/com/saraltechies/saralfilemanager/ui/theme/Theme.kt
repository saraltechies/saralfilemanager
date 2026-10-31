package com.saraltechies.saralfilemanager.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Saral Techies brand palette, taken from saraltechies.com.
val BrandBlue = Color(0xFF0D6EFD)
val BrandBlueDark = Color(0xFF0A58CA)
val BrandNavy = Color(0xFF0D1B4A)
val BrandInk = Color(0xFF1A1A2E)
val BrandSlate = Color(0xFF64748B)
val BrandMist = Color(0xFFF0F6FF)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = BrandInk,
    background = Color(0xFFF8FAFF),
    onBackground = BrandInk,
    surface = Color.White,
    onSurface = BrandInk,
    surfaceVariant = BrandMist,
    onSurfaceVariant = BrandSlate,
    surfaceContainer = BrandMist,
    outline = Color(0xFFCBD5E1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = BrandNavy,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF93C5FD),
    onSecondary = BrandNavy,
    secondaryContainer = Color(0xFF1E2A55),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1230),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1838),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1A2550),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF141E44),
    outline = Color(0xFF334155),
)

@Composable
fun SaralFileManagerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // The system bars are transparent (edge-to-edge), so their icons must follow the app's theme,
    // which matches the phone's light or dark setting.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
