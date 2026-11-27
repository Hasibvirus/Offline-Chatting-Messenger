package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrightCyan,
    onPrimary = SlateNavyDeep,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = SoftTealContainer,
    secondary = BrightAmber,
    onSecondary = SlateNavyDeep,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = AmberContainer,
    tertiary = Color(0xFF7DD3FC),
    onTertiary = SlateNavyDeep,
    error = Color(0xFFFF8A80),
    onError = EmergencyDarkBg,
    errorContainer = EmergencyDarkBg,
    onErrorContainer = EmergencyLightBg,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricTeal,
    onPrimary = Color.White,
    primaryContainer = SoftTealContainer,
    onPrimaryContainer = DeepTealContainer,
    secondary = SignalAmber,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = DarkAmberContainer,
    tertiary = SlateNavyLight,
    onTertiary = Color.White,
    error = EmergencyCrimson,
    onError = Color.White,
    errorContainer = EmergencyLightBg,
    onErrorContainer = EmergencyDarkBg,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OfflineMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isBengali: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildAppTypography(isBengali),
        shapes = AppShapes,
        content = content
    )
}
