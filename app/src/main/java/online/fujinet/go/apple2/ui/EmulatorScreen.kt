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
    var keyboardVisible by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        ControlBar(
            keyboardVisible = keyboardVisible,
            onToggleKeyboard = { keyboardVisible = !keyboardVisible },
            onReset = session::reset,
            onOpenFujiNet = onOpenFujiNet,
            onShutdown = onShutdown,
        )

        EmulatorSurface(
            session = session,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        if (keyboardVisible) {
            AppleKeyboard(session = session)
        }
    }
}

@Composable
private fun ControlBar(
    keyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit,
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
        BarButton(if (keyboardVisible) "Hide ⌨" else "⌨", Modifier.weight(1f), onToggleKeyboard)
        BarButton("Reset", Modifier.weight(1f), onReset)
        BarButton("FujiNet", Modifier.weight(1f), onOpenFujiNet)
        BarButton("Power", Modifier.weight(1f), onShutdown)
    }
}

@Composable
private fun BarButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}
