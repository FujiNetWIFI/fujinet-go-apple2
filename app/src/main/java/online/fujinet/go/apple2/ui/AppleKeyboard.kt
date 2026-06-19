package online.fujinet.go.apple2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.fujinet.go.apple2.SessionController
import online.fujinet.go.apple2.input.Retro

/** One printable key: its face, the unshifted/shifted ASCII it emits, and the RETROK code. */
private data class Key(
    val face: String,
    val shiftFace: String = face,
    val code: Int,
    val ascii: Int,
    val shiftAscii: Int = ascii,
    val weight: Float = 1f,
)

private val ROW1 = listOf(
    Key("1", "!", Retro.K_0 + 1, '1'.code, '!'.code),
    Key("2", "@", Retro.K_0 + 2, '2'.code, '@'.code),
    Key("3", "#", Retro.K_0 + 3, '3'.code, '#'.code),
    Key("4", "$", Retro.K_0 + 4, '4'.code, '$'.code),
    Key("5", "%", Retro.K_0 + 5, '5'.code, '%'.code),
    Key("6", "^", Retro.K_0 + 6, '6'.code, '^'.code),
    Key("7", "&", Retro.K_0 + 7, '7'.code, '&'.code),
    Key("8", "*", Retro.K_0 + 8, '8'.code, '*'.code),
    Key("9", "(", Retro.K_0 + 9, '9'.code, '('.code),
    Key("0", ")", Retro.K_0, '0'.code, ')'.code),
    Key("-", "_", '-'.code, '-'.code, '_'.code),
    Key("=", "+", '='.code, '='.code, '+'.code),
)

private fun letter(c: Char) = Key(c.uppercase(), c.uppercase(), Retro.K_a + (c - 'a'), c.code, c.uppercaseChar().code)

private val ROW2 = "qwertyuiop".map { letter(it) } +
    Key("[", "{", '['.code, '['.code, '{'.code) + Key("]", "}", ']'.code, ']'.code, '}'.code)
private val ROW3 = "asdfghjkl".map { letter(it) } +
    Key(";", ":", ';'.code, ';'.code, ':'.code) + Key("'", "\"", '\''.code, '\''.code, '"'.code)
private val ROW4 = "zxcvbnm".map { letter(it) } +
    Key(",", "<", ','.code, ','.code, '<'.code) + Key(".", ">", '.'.code, '.'.code, '>'.code) +
    Key("/", "?", '/'.code, '/'.code, '?'.code)

/**
 * On-screen Apple II keyboard. Shift and Ctrl are sticky one-shot modifiers
 * (cleared after the next key); Open-Apple and Closed-Apple are sticky toggles
 * (held until tapped again) since games hold them with another key.
 */
@Composable
fun AppleKeyboard(session: SessionController, modifier: Modifier = Modifier) {
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var openApple by remember { mutableStateOf(false) }
    var closedApple by remember { mutableStateOf(false) }

    fun emit(code: Int, ascii: Int, shiftAscii: Int) {
        val mods = (if (shift) Retro.MOD_SHIFT else 0) or (if (ctrl) Retro.MOD_CTRL else 0)
        val ch = when {
            ctrl -> 0                       // Ctrl combos resolve via keycode in the core
            shift -> shiftAscii
            else -> ascii
        }
        session.tapKey(code, ch, mods)
        shift = false
        ctrl = false
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KeyRow(ROW1, shift) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW2, shift) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW3, shift) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW4, shift) { emit(it.code, it.ascii, it.shiftAscii) }

        // Bottom control row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ModKey("CTRL", ctrl, Modifier.weight(1.2f)) { ctrl = !ctrl }
            ModKey("SHIFT", shift, Modifier.weight(1.2f)) { shift = !shift }
            ModKey("⎇", openApple, Modifier.weight(1f)) {
                openApple = !openApple
                if (openApple) session.keyDown(Retro.K_LALT, 0, 0) else session.keyUp(Retro.K_LALT, 0, 0)
            }
            TapKey("ESC", Modifier.weight(1f)) { session.tapKey(Retro.K_ESCAPE, 27, 0) }
            TapKey("SPACE", Modifier.weight(3f)) { session.tapKey(Retro.K_SPACE, 32, 0); shift = false; ctrl = false }
            ModKey("◆", closedApple, Modifier.weight(1f)) {
                closedApple = !closedApple
                if (closedApple) session.keyDown(Retro.K_RALT, 0, 0) else session.keyUp(Retro.K_RALT, 0, 0)
            }
            TapKey("DEL", Modifier.weight(1f)) { session.tapKey(Retro.K_DELETE, 127, 0) }
            TapKey("RET", Modifier.weight(1.4f)) { session.tapKey(Retro.K_RETURN, 13, 0) }
        }

        // Arrow row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TapKey("TAB", Modifier.weight(1f)) { session.tapKey(Retro.K_TAB, 9, 0) }
            TapKey("←", Modifier.weight(1f)) { session.tapKey(Retro.K_LEFT, 0, 0) }
            TapKey("↓", Modifier.weight(1f)) { session.tapKey(Retro.K_DOWN, 0, 0) }
            TapKey("↑", Modifier.weight(1f)) { session.tapKey(Retro.K_UP, 0, 0) }
            TapKey("→", Modifier.weight(1f)) { session.tapKey(Retro.K_RIGHT, 0, 0) }
        }
    }
}

@Composable
private fun KeyRow(keys: List<Key>, shift: Boolean, onKey: (Key) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (k in keys) {
            TapKey(if (shift) k.shiftFace else k.face, Modifier.weight(k.weight)) { onKey(k) }
        }
    }
}

@Composable
private fun TapKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun ModKey(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = if (active) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
