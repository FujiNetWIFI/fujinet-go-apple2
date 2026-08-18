package online.fujinet.go.apple2.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import online.fujinet.go.apple2.MediaImport
import online.fujinet.go.apple2.R
import online.fujinet.go.apple2.settings.RomStore

private val GateAccent = Color(0xFF8D84E5)
private val GateBackground = Color(0xFF0D0B1A)
private val GateBody = Color(0xFFE9E7F5)

/**
 * Shown in place of the emulator surface until the Enhanced //e ROM set is
 * present -- release builds embed no Apple firmware (see COMPLIANCE.md), so
 * this is every user's first-run screen unless they built with
 * -Papple2Roms=true. The three files have distinct sizes, so one picker
 * classifies them. [onImported] is called after each import attempt so the
 * caller can re-check [RomStore.availableMachines].
 */
@Composable
fun RomGate(onImported: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    val status = remember(refreshToken) { RomStore.status(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = MediaImport.importSystemRom(context, uri)) {
            is MediaImport.RomImportResult.Success -> {
                val note = if (result.crcMatches) "" else
                    " (note: not the reference dump -- most revisions work)"
                Toast.makeText(context, "Imported ${result.fileName}$note", Toast.LENGTH_LONG).show()
            }
            is MediaImport.RomImportResult.WrongSize -> {
                Toast.makeText(
                    context,
                    "Not a recognized ROM (${result.actualSize} bytes; expected 16384, 4096, or 256)",
                    Toast.LENGTH_LONG,
                ).show()
            }
            MediaImport.RomImportResult.ReadFailed -> {
                Toast.makeText(context, "Could not read the selected file", Toast.LENGTH_SHORT).show()
            }
        }
        refreshToken++
        onImported()
    }

    Column(
        modifier = modifier.fillMaxSize().background(GateBackground)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.fujinet_toolbar),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Text(
            "Apple II system ROMs required",
            style = MaterialTheme.typography.titleLarge,
            color = GateAccent,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            "Import the Enhanced //e system ROM, its video ROM, and the " +
                "Disk ][ boot ROM from your own Apple II to boot.",
            style = MaterialTheme.typography.bodyMedium,
            color = GateBody,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            status.forEach { rom ->
                Text(
                    (if (rom.present) "✓ " else "✗ ") +
                        "${rom.rom.label} (${rom.rom.fileName}, ${rom.rom.expectedSize} B)",
                    color = if (rom.present) GateAccent else GateBody,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("Import ROMs…")
        }
    }
}
