package `in`.vidyadhan.academy.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import `in`.vidyadhan.academy.R

/** Academy teal and marigold (from the owner's prototype), plus supporting tones. Blue* = teal, Saffron* = marigold. */
object Brand {
    val Blue50 = Color(0xFFF2FAFC)
    val Blue100 = Color(0xFFE0F2F5)
    val Blue200 = Color(0xFFB3DCE4)
    val Blue300 = Color(0xFF7FC2D0)
    val Blue400 = Color(0xFF3E9DB2)
    val Blue500 = Color(0xFF1E7F93)
    val Blue600 = Color(0xFF166478)
    val Blue700 = Color(0xFF0F4C5C)
    val Blue800 = Color(0xFF0C4150)
    val Blue900 = Color(0xFF0A3642)
    val Blue950 = Color(0xFF072A33)

    val Saffron50 = Color(0xFFFEF8F2)
    val Saffron100 = Color(0xFFFDF0E4)
    val Saffron200 = Color(0xFFF6CFA9)
    val Saffron400 = Color(0xFFEA9550)
    val Saffron500 = Color(0xFFE07A2B)
    val Saffron600 = Color(0xFFC9661E)
    val Saffron700 = Color(0xFFA9541A)
    val Saffron800 = Color(0xFF864316)

    val Green50 = Color(0xFFECFDF3)
    val Green100 = Color(0xFFDCFCE7)
    val Green500 = Color(0xFF22C55E)
    val Green600 = Color(0xFF16A34A)
    val Green700 = Color(0xFF15803D)
    val Red50 = Color(0xFFFEF2F2)
    val Red100 = Color(0xFFFEE2E2)
    val Red500 = Color(0xFFEF4444)
    val Red600 = Color(0xFFDC2626)
    val Red700 = Color(0xFFB91C1C)
    val Amber50 = Color(0xFFFFFBEB)
    val Amber600 = Color(0xFFD97706)
    val Amber700 = Color(0xFFB45309)
    val Teal500 = Color(0xFF14B8A6)
    val Teal700 = Color(0xFF0F766E)
    val Violet500 = Color(0xFF8B5CF6)
    val Violet700 = Color(0xFF6D28D9)
    val WhatsApp = Color(0xFF25D366)
    val WhatsAppDark = Color(0xFF128C7E)

    val Ink = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate600 = Color(0xFF475569)
    val Slate500 = Color(0xFF526077)
    val Slate400 = Color(0xFF8A97AB)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate200 = Color(0xFFE2E8F0)
    val Line = Color(0xFFE8EEF5)
    val Bg = Color(0xFFF3F6FA)
    val Slate50 = Color(0xFFF8FAFC)
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Mukta is the website's font and has beautiful Marathi letters. */
val Mukta = FontFamily(
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.Bold),
)

private fun s(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = Jakarta, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.em)

private val AppType = Typography(
    displayLarge = s(44, FontWeight.ExtraBold, 52, -0.02),
    displayMedium = s(38, FontWeight.ExtraBold, 46, -0.02),
    displaySmall = s(32, FontWeight.ExtraBold, 40, -0.02),
    headlineLarge = s(28, FontWeight.ExtraBold, 36, -0.015),
    headlineMedium = s(25, FontWeight.Bold, 32, -0.015),
    headlineSmall = s(21, FontWeight.Bold, 28, -0.01),
    titleLarge = s(18, FontWeight.Bold, 25, -0.005),
    titleMedium = s(16, FontWeight.SemiBold, 22),
    titleSmall = s(14, FontWeight.SemiBold, 20),
    bodyLarge = s(16, FontWeight.Normal, 24),
    bodyMedium = s(14, FontWeight.Normal, 20),
    bodySmall = s(12, FontWeight.Medium, 17),
    labelLarge = s(14, FontWeight.SemiBold, 20),
    labelMedium = s(12, FontWeight.SemiBold, 16, 0.01),
    labelSmall = s(11, FontWeight.SemiBold, 14, 0.02),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Scheme = lightColorScheme(
    primary = Brand.Blue700,
    onPrimary = Color.White,
    primaryContainer = Brand.Blue100,
    onPrimaryContainer = Brand.Blue900,
    secondary = Brand.Saffron600,
    onSecondary = Color.White,
    secondaryContainer = Brand.Saffron100,
    onSecondaryContainer = Brand.Saffron800,
    tertiary = Brand.Teal700,
    background = Brand.Bg,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Blue50,
    onSurfaceVariant = Brand.Slate500,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Brand.Slate50,
    outline = Brand.Slate200,
    outlineVariant = Brand.Line,
    error = Brand.Red600,
)

@Composable
fun VidyadhanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppType, shapes = AppShapes, content = content)
}
