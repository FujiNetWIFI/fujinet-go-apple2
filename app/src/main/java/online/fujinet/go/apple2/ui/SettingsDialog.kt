package online.fujinet.go.apple2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.fujinet.go.apple2.Apple2Config
import online.fujinet.go.apple2.MACHINES
import online.fujinet.go.apple2.SLOT3_CARDS
import online.fujinet.go.apple2.SLOT4_CARDS
import online.fujinet.go.apple2.SLOT5_CARDS
import online.fujinet.go.apple2.SLOT7_CARDS

/**
 * Machine + expansion-slot settings. Applying a change restarts the session so
 * the new machine type / slot cards take effect (the libretro core reads its
 * options at load). FujiNet is the SmartPort-over-SLIP card; keep it in a slot
 * (slot 7 by default) for the FujiNet drive to be present.
 */
@Composable
fun SettingsDialog(
    config: Apple2Config,
    keyboardHaptics: Boolean,
    joystickHaptics: Boolean,
    onApply: (Apple2Config) -> Unit,
    onKeyboardHapticsChange: (Boolean) -> Unit,
    onJoystickHapticsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (draft != config) onApply(draft)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OptionRow("Apple II model", MACHINES, draft.machine) { draft = draft.copy(machine = it) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Expansion slots", style = MaterialTheme.typography.titleSmall)

                OptionRow("Slot 3", SLOT3_CARDS, draft.slot3) { draft = draft.copy(slot3 = it) }
                OptionRow("Slot 4", SLOT4_CARDS, draft.slot4) { draft = draft.copy(slot4 = it) }
                OptionRow("Slot 5", SLOT5_CARDS, draft.slot5) { draft = draft.copy(slot5 = it) }
                OptionRow("Slot 7", SLOT7_CARDS, draft.slot7) { draft = draft.copy(slot7 = it) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Haptics", style = MaterialTheme.typography.titleSmall)
                // Haptics apply live (no session restart), so they call back immediately
                // rather than going through the draft/Apply path the machine options use.
                ToggleRow("Keyboard haptics", keyboardHaptics, onKeyboardHapticsChange)
                ToggleRow("Joystick haptics", joystickHaptics, onJoystickHapticsChange)
            }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected, textAlign = TextAlign.End)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}
