package app.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MtDark = darkColorScheme(
    primary            = Color(0xFF22D3A5),
    onPrimary          = Color(0xFF00261A),
    primaryContainer   = Color(0xFF0E7A60),
    onPrimaryContainer = Color(0xFFB7F2D8),
    secondary          = Color(0xFF7CC2FF),
    onSecondary        = Color(0xFF00253D),
    background         = Color(0xFF070C18),
    onBackground       = Color(0xFFEAF2FF),
    surface            = Color(0xFF101A33),
    onSurface          = Color(0xFFEAF2FF),
    surfaceVariant     = Color(0xFF182648),
    onSurfaceVariant   = Color(0xFF8AA2C7),
    error              = Color(0xFFFF6B6B),
    onError            = Color(0xFF1A0000),
    outline            = Color(0xFF2A3B5E),
    outlineVariant     = Color(0xFF1B2A48),
)

private val MtLight = lightColorScheme(
    primary            = Color(0xFF0E8F70),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFB7F2D8),
    onPrimaryContainer = Color(0xFF00261A),
    secondary          = Color(0xFF2563EB),
    background         = Color(0xFFF7F9FC),
    onBackground       = Color(0xFF0B1220),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF0B1220),
    surfaceVariant     = Color(0xFFEAF0F8),
    onSurfaceVariant   = Color(0xFF466080),
    outline            = Color(0xFFCBD6E4),
)

private val MtTypography = Typography(
    displayLarge   = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 56.sp, letterSpacing = (-1.0).sp),
    displayMedium  = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 30.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 24.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 20.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 16.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 16.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 14.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 12.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, letterSpacing = 0.4.sp),
)

@Composable
fun MoneyTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) MtDark else MtLight,
        typography  = MtTypography,
        content     = content,
    )
}

@Composable
@ReadOnlyComposable
fun bgGradient(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = listOf(cs.background, cs.surface, cs.background)
    )
}
