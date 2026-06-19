package online.fujinet.go.apple2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Accent derived from the FujiNet Go Apple2 launcher icon's red background
// (#F44336), with dark warm-neutral surfaces to match.
private val FujiRed = Color(0xFFF44336)
private val FujiDark = Color(0xFF160C0B)
private val FujiPanel = Color(0xFF241413)

private val DarkColors = darkColorScheme(
    primary = FujiRed,
    onPrimary = Color.White,
    background = FujiDark,
    surface = FujiPanel,
    onSurface = Color(0xFFECE6E5),
)

private val LightColors = lightColorScheme(
    primary = FujiRed,
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
