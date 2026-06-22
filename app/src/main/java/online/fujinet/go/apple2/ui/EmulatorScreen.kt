package online.fujinet.go.apple2.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.fujinet.go.apple2.SessionController

/**
 * The main app screen: the Apple II video surface, a thin control bar (toggle
 * keyboard, Ctrl-Reset, open the FujiNet web UI, shut down), and the on-screen
 * keyboard. Mirrors fujinet-go-adam's EmulatorScreen, Apple-ised.
 */
@Composable
fun EmulatorScreen(
    session: SessionController,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The keyboard and joystick are mutually exclusive: at most one input overlay
    // is shown so the emulator surface keeps as much room as possible.
    var overlay by remember { mutableStateOf(Overlay.KEYBOARD) }
    var showSettings by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showSettings) {
        SettingsDialog(
            config = session.config,
            onApply = { session.applyConfig(it) },
            onDismiss = { showSettings = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ControlBar(
            keyboardActive = overlay == Overlay.KEYBOARD,
            joystickActive = overlay == Overlay.JOYSTICK,
            onToggleKeyboard = {
                overlay = if (overlay == Overlay.KEYBOARD) Overlay.NONE else Overlay.KEYBOARD
            },
            onToggleJoystick = {
                overlay = if (overlay == Overlay.JOYSTICK) Overlay.NONE else Overlay.JOYSTICK
            },
            onSettings = { showSettings = true },
            onOpenFujiNet = onOpenFujiNet,
            onShutdown = onShutdown,
        )

        if (landscape && overlay == Overlay.JOYSTICK) {
            // Landscape: flank the screen with the stick (left) and paddle buttons
            // (right) so the surface fills the full height between them.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                JoystickPad(
                    onAxis = { x, y -> session.paddle(x, y) },
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp),
                )
                EmulatorSurface(
                    session = session,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FireButtons(
                    session,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 12.dp),
                )
            }
        } else {
            // Portrait (and keyboard): the surface fills the area above a stacked
            // bottom overlay.
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
            }
            when (overlay) {
                Overlay.KEYBOARD -> AppleKeyboard(session = session)
                Overlay.JOYSTICK -> JoystickView(session = session)
                Overlay.NONE -> {}
            }
        }
    }
}

private enum class Overlay { NONE, KEYBOARD, JOYSTICK }

@Composable
private fun ControlBar(
    keyboardActive: Boolean,
    joystickActive: Boolean,
    onToggleKeyboard: () -> Unit,
    onToggleJoystick: () -> Unit,
    onSettings: () -> Unit,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarButton("⌨", Modifier.weight(1f), keyboardActive, onToggleKeyboard)
        BarButton("Joy", Modifier.weight(1f), joystickActive, onToggleJoystick)
        BarButton("⚙", Modifier.weight(1f), onClick = onSettings)
        BarButton("FujiNet", Modifier.weight(1f), onClick = onOpenFujiNet)
        BarButton("Power", Modifier.weight(1f), onClick = onShutdown)
    }
}

@Composable
private fun BarButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}
