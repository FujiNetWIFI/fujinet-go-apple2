package online.fujinet.go.apple2.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
 *
 * Keys shrink to a compact height on TV and other short screens (e.g. landscape)
 * so the keyboard doesn't eat most of the display.
 */
@Composable
fun AppleKeyboard(session: SessionController, modifier: Modifier = Modifier) {
    var shift by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var openApple by remember { mutableStateOf(false) }
    var closedApple by remember { mutableStateOf(false) }

    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val compact = isTv || config.screenHeightDp < 480
    val keyH: Dp = if (compact) 28.dp else 46.dp
    val font: TextUnit = if (compact) 11.sp else 13.sp
    val gap = if (compact) 1.dp else 2.dp

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
        modifier = modifier.fillMaxWidth().padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        KeyRow(ROW1, shift, keyH, font, gap) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW2, shift, keyH, font, gap) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW3, shift, keyH, font, gap) { emit(it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW4, shift, keyH, font, gap) { emit(it.code, it.ascii, it.shiftAscii) }

        // Bottom control row.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeyButton("CTRL", Modifier.weight(1.2f), keyH, font, ctrl) { ctrl = !ctrl }
            KeyButton("SHIFT", Modifier.weight(1.2f), keyH, font, shift) { shift = !shift }
            // Open Apple = open (outline) square, Closed/Solid Apple = filled square.
            KeyButton("□", Modifier.weight(1f), keyH, font, openApple) {
                openApple = !openApple
                if (openApple) session.keyDown(Retro.K_LALT, 0, 0) else session.keyUp(Retro.K_LALT, 0, 0)
            }
            KeyButton("ESC", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_ESCAPE, 27, 0) }
            KeyButton("SPACE", Modifier.weight(3f), keyH, font) { session.tapKey(Retro.K_SPACE, 32, 0); shift = false; ctrl = false }
            KeyButton("■", Modifier.weight(1f), keyH, font, closedApple) {
                closedApple = !closedApple
                if (closedApple) session.keyDown(Retro.K_RALT, 0, 0) else session.keyUp(Retro.K_RALT, 0, 0)
            }
            KeyButton("DEL", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_DELETE, 127, 0) }
            KeyButton("RET", Modifier.weight(1.4f), keyH, font) { session.tapKey(Retro.K_RETURN, 13, 0) }
        }

        // Arrow row.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeyButton("TAB", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_TAB, 9, 0) }
            KeyButton("←", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_LEFT, 0, 0) }
            KeyButton("↓", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_DOWN, 0, 0) }
            KeyButton("↑", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_UP, 0, 0) }
            KeyButton("→", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_RIGHT, 0, 0) }
            // Apple II RESET: like the real machine it only resets together with
            // Ctrl (Ctrl-RESET). Highlights while Ctrl is held to show it's armed.
            KeyButton("RESET", Modifier.weight(1.5f), keyH, font, ctrl) {
                if (ctrl) {
                    session.reset()
                    ctrl = false
                }
            }
        }
    }
}

@Composable
private fun KeyRow(keys: List<Key>, shift: Boolean, keyH: Dp, font: TextUnit, gap: Dp, onKey: (Key) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (k in keys) {
            KeyButton(if (shift) k.shiftFace else k.face, Modifier.weight(k.weight), keyH, font) { onKey(k) }
        }
    }
}

/**
 * A keyboard key. A height-controlled Box (not a Material Button, whose 40dp
 * minimum height blocks the compact TV layout). Active modifier keys invert to
 * make their sticky state obvious.
 */
@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    keyH: Dp,
    font: TextUnit,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val container = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val content = if (active) MaterialTheme.colorScheme.primary else Color.White
    Box(
        modifier = modifier
            .height(keyH)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = font, color = content, textAlign = TextAlign.Center, maxLines = 1)
    }
}
