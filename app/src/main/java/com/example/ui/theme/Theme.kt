package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ReiDarkColorScheme = darkColorScheme(
    primary = ElectricIndigoBright,
    onPrimary = Color(0xFF090D16),
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = OnIndigoContainerDark,
    secondary = TelemetryCyan,
    onSecondary = Color(0xFF041E27),
    secondaryContainer = TelemetryCyanContainerDark,
    onSecondaryContainer = OnTelemetryCyanDark,
    tertiary = EmeraldAiCore,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = ObsidianSlateBg,
    onBackground = Color(0xFFF1F5F9),
    surface = MidnightSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ElevatedSlateCard,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = SlateBorderDark,
    outlineVariant = Color(0xFF1E293B),
    error = CoralQ1Urgent,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFFE4E6)
)

private val ReiLightColorScheme = lightColorScheme(
    primary = RoyalIndigoLight,
    onPrimary = Color.White,
    primaryContainer = IndigoContainerLight,
    onPrimaryContainer = OnIndigoContainerLight,
    secondary = TelemetryCyanLight,
    onSecondary = Color.White,
    secondaryContainer = TelemetryCyanContainerLight,
    onSecondaryContainer = OnTelemetryCyanLight,
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = Color(0xFF064E3B),
    background = AlabasterPaperBg,
    onBackground = Color(0xFF0F172A),
    surface = PureWhiteSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = Color(0xFF475569),
    outline = SlateBorderLight,
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = Color(0xFF881337)
)

val ReiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ReiExecutiveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ReiDarkColorScheme else ReiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ReiShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    ReiExecutiveTheme(darkTheme = darkTheme, content = content)
}
