package `in`.vidyadhan.academy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same colours as the Vidyadhan Academy website.
object Brand {
    val Blue50 = Color(0xFFEEF7FC)
    val Blue100 = Color(0xFFD6ECF7)
    val Blue500 = Color(0xFF1A7DB2)
    val Green700 = Color(0xFF15803D)
    val Green100 = Color(0xFFDCFCE7)
    val Red700 = Color(0xFFB91C1C)
    val Red100 = Color(0xFFFEE2E2)
    val Blue700 = Color(0xFF0B5583)
    val Blue800 = Color(0xFF0D466B)
    val Blue900 = Color(0xFF0F3A58)
    val Saffron100 = Color(0xFFFFEDD5)
    val Saffron700 = Color(0xFFC2410C)
    val Saffron800 = Color(0xFF9A3412)
    val Slate50 = Color(0xFFF8FAFC)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate500 = Color(0xFF526077)
    val Slate800 = Color(0xFF1E293B)
}

private val Scheme = lightColorScheme(
    primary = Brand.Blue700,
    onPrimary = Color.White,
    primaryContainer = Brand.Blue100,
    onPrimaryContainer = Brand.Blue900,
    secondary = Brand.Saffron700,
    onSecondary = Color.White,
    secondaryContainer = Brand.Saffron100,
    onSecondaryContainer = Brand.Saffron800,
    background = Brand.Slate50,
    onBackground = Brand.Slate800,
    surface = Color.White,
    onSurface = Brand.Slate800,
    surfaceVariant = Brand.Blue50,
    onSurfaceVariant = Brand.Slate500,
    outline = Brand.Slate200,
)

@Composable
fun VidyadhanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
