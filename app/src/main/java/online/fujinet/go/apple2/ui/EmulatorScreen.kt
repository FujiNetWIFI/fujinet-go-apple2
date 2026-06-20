package online.fujinet.go.apple2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // The keyboard and joystick are mutually exclusive: at most one bottom input
    // overlay is shown so the emulator surface keeps as much room as possible.
    var overlay by remember { mutableStateOf(Overlay.KEYBOARD) }

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
            onReset = session::reset,
            onOpenFujiNet = onOpenFujiNet,
            onShutdown = onShutdown,
        )

        EmulatorSurface(
            session = session,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        when (overlay) {
            Overlay.JOYSTICK -> JoystickView(session = session)
            Overlay.KEYBOARD -> AppleKeyboard(session = session)
            Overlay.NONE -> {}
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
    onReset: () -> Unit,
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
        BarButton("Reset", Modifier.weight(1f), onClick = onReset)
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
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}
