package app.boardwalk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF31694A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EDD8), onPrimaryContainer = Color(0xFF133922),
    secondary = Color(0xFF536454), secondaryContainer = Color(0xFFE0E9DC),
    background = Color(0xFFFAFBF6), surface = Color(0xFFFAFBF6),
    surfaceContainer = Color(0xFFF0F3EB), surfaceContainerLow = Color(0xFFF4F6F0),
    surfaceContainerHighest = Color(0xFFE5E9E0), onSurface = Color(0xFF1A211B),
    onSurfaceVariant = Color(0xFF4D574D), outlineVariant = Color(0xFFD3DAD0),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA0D4AB), onPrimary = Color(0xFF07391C),
    primaryContainer = Color(0xFF254D32), onPrimaryContainer = Color(0xFFD2EDD6),
    secondary = Color(0xFFB5CBB5), secondaryContainer = Color(0xFF354638),
    background = Color(0xFF111712), surface = Color(0xFF111712),
    surfaceContainer = Color(0xFF1D241E), surfaceContainerLow = Color(0xFF192019),
    surfaceContainerHighest = Color(0xFF303A30), onSurface = Color(0xFFE1E9DE),
    onSurfaceVariant = Color(0xFFBDC9BB), outlineVariant = Color(0xFF404C40),
)

@Composable
fun BoardwalkTheme(theme: String = "System", content: @Composable () -> Unit) {
    val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
