package com.crm.phoneagent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Brand blue matches the CRM web frontend (Tailwind blue-700), so the phone and the CRM read as
// one product rather than two unrelated apps.
private val BrandBlue = Color(0xFF1D4ED8)
private val BrandBlueDark = Color(0xFF3B82F6)
private val SuccessGreen = Color(0xFF16A34A)
private val DangerRed = Color(0xFFDC2626)

val LocalSuccessColor = androidx.compose.runtime.staticCompositionLocalOf { SuccessGreen }
val LocalDangerColor = androidx.compose.runtime.staticCompositionLocalOf { DangerRed }

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF475569),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    error = DangerRed,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFFCBD5E1),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
)

@Composable
fun PhoneAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PhoneAgentTypography,
        content = content,
    )
}
