package online.fujinet.go.apple2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent in the spirit of the FujiNet Go family launcher icons (dark-blue
// background), brightened for legibility on the dark UI.
private val FujiBlue = Color(0xFF4A86E8)
private val FujiDark = Color(0xFF0B1326)
private val FujiPanel = Color(0xFF16233F)

private val DarkColors = darkColorScheme(
    primary = FujiBlue,
    onPrimary = Color.White,
    background = FujiDark,
    surface = FujiPanel,
    onSurface = Color(0xFFE6E6E6),
)

private val LightColors = lightColorScheme(
    primary = FujiBlue,
    onPrimary = Color.White,
)

@Composable
fun FujiNetGoApple2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
