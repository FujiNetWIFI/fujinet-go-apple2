package online.fujinet.go.apple2.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
 * Sticky-modifier state shared by the keyboard layouts. Shift and Ctrl are one-shot
 * (cleared after the next key); Open-Apple and Closed-Apple are toggles held until
 * tapped again since games hold them alongside another key. The behaviours that touch
 * the session live here so the full portrait keyboard and the split landscape halves
 * stay in sync from a single source of truth.
 */
private class KeyboardModifiers {
    var shift by mutableStateOf(false)
    var ctrl by mutableStateOf(false)
    var openApple by mutableStateOf(false)
    var closedApple by mutableStateOf(false)

    fun emit(session: SessionController, code: Int, ascii: Int, shiftAscii: Int) {
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

    fun space(session: SessionController) {
        session.tapKey(Retro.K_SPACE, 32, 0)
        shift = false
        ctrl = false
    }

    // Open Apple = open (outline) square, Closed/Solid Apple = filled square; both are
    // sticky holds emitting key-down/up so they can be held with another key.
    fun toggleOpenApple(session: SessionController) {
        openApple = !openApple
        if (openApple) session.keyDown(Retro.K_LALT, 0, 0) else session.keyUp(Retro.K_LALT, 0, 0)
    }

    fun toggleClosedApple(session: SessionController) {
        closedApple = !closedApple
        if (closedApple) session.keyDown(Retro.K_RALT, 0, 0) else session.keyUp(Retro.K_RALT, 0, 0)
    }

    // Apple II RESET: like the real machine it only resets together with Ctrl.
    // Ctrl-RESET warm-resets to BASIC; adding Open-Apple (Ctrl-OA-RESET) cold-boots,
    // as on a real //e. Releases Open-Apple after the gesture, mirroring the keys
    // being let go on real hardware.
    fun reset(session: SessionController) {
        if (ctrl) {
            session.reset(cold = openApple)
            ctrl = false
            if (openApple) {
                openApple = false
                session.keyUp(Retro.K_LALT, 0, 0)
            }
        }
    }
}

@Composable
private fun rememberKeyboardModifiers() = remember { KeyboardModifiers() }

/**
 * On-screen Apple II keyboard, full-width. Used in portrait and as the fallback for
 * landscapes too narrow for [LandscapeSplitKeyboard].
 *
 * Keys shrink to a compact height on TV and other short screens so the keyboard
 * doesn't eat most of the display.
 */
@Composable
fun AppleKeyboard(session: SessionController, modifier: Modifier = Modifier, hapticsEnabled: Boolean = true) {
    val mods = rememberKeyboardModifiers()

    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val compact = isTv || config.screenHeightDp < 480
    val keyH: Dp = if (compact) 28.dp else 46.dp
    val font: TextUnit = if (compact) 11.sp else 13.sp
    val gap = if (compact) 1.dp else 2.dp

    val emitHaptic = rememberFujiHaptic(FujiHapticPattern.KeyPress)
    val onHaptic = { if (hapticsEnabled) emitHaptic() }

    // Every keycap routes its tap through KeyButton; rather than thread an onHaptic
    // through all ~13 call sites, expose the gated pulse via a CompositionLocal that
    // KeyButton reads and fires on click.
    CompositionLocalProvider(LocalKeyHaptic provides onHaptic) {
    Column(
        modifier = modifier.fillMaxWidth().padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        KeyRow(ROW1, mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW2, mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW3, mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
        KeyRow(ROW4, mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }

        // Bottom control row.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            KeyButton("CTRL", Modifier.weight(1.2f), keyH, font, mods.ctrl) { mods.ctrl = !mods.ctrl }
            KeyButton("SHIFT", Modifier.weight(1.2f), keyH, font, mods.shift) { mods.shift = !mods.shift }
            KeyButton("□", Modifier.weight(1f), keyH, font, mods.openApple) { mods.toggleOpenApple(session) }
            KeyButton("ESC", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_ESCAPE, 27, 0) }
            KeyButton("SPACE", Modifier.weight(3f), keyH, font) { mods.space(session) }
            KeyButton("■", Modifier.weight(1f), keyH, font, mods.closedApple) { mods.toggleClosedApple(session) }
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
            KeyButton("RESET", Modifier.weight(1.5f), keyH, font, mods.ctrl) { mods.reset(session) }
        }
    }
    }
}

/** Minimum flank width worth splitting into; below this we keep the stacked keyboard. */
private val MIN_HALF_WIDTH = 150.dp
/** Cap so keys don't grow absurdly tall on very tall landscape panels. */
private val MAX_SPLIT_KEY_HEIGHT = 64.dp

/**
 * Landscape keyboard that flanks the emulator display with two halves placed in the
 * pillar-box margins beside the 4:3 picture, so the display keeps full height between
 * them. Each flank is sized to exactly the side-space the picture leaves free, and the
 * keys grow to fill the tall column. On screens too narrow to fit usable halves this
 * falls back to the stacked layout (surface above a full-width [AppleKeyboard]).
 *
 * Both halves share one [KeyboardModifiers], so a Shift/Ctrl tapped on one half applies
 * to a key tapped on the other.
 */
@Composable
fun LandscapeSplitKeyboard(
    session: SessionController,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier) {
        // Width the 4:3 picture leaves free on each side when it fills the height.
        val sideWidth = (maxWidth - maxHeight * FRAME_RATIO) / 2

        if (sideWidth < MIN_HALF_WIDTH) {
            // Too narrow to split -- keep the familiar stacked layout.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
                }
                AppleKeyboard(session = session, hapticsEnabled = hapticsEnabled)
            }
        } else {
            val mods = rememberKeyboardModifiers()
            val gap = 2.dp
            // Six rows fill the column height; cap so keys stay a sane size.
            val keyH = ((maxHeight - gap * 5) / 6).coerceAtMost(MAX_SPLIT_KEY_HEIGHT)
            val font: TextUnit = 16.sp

            val emitHaptic = rememberFujiHaptic(FujiHapticPattern.KeyPress)
            val onHaptic = { if (hapticsEnabled) emitHaptic() }

            CompositionLocalProvider(LocalKeyHaptic provides onHaptic) {
                Row(Modifier.fillMaxSize()) {
                    KeyboardHalf(Modifier.width(sideWidth), left = true, session, mods, keyH, font, gap)
                    EmulatorSurface(session = session, modifier = Modifier.weight(1f).fillMaxHeight())
                    KeyboardHalf(Modifier.width(sideWidth), left = false, session, mods, keyH, font, gap)
                }
            }
        }
    }
}

/**
 * One half of the split landscape keyboard: six rows stacked to fill the flank height.
 * The four alphanumeric rows are sliced from the shared row data; the two bottom rows
 * (modifiers, space, arrows, reset) are split by hand across the halves.
 */
@Composable
private fun KeyboardHalf(
    modifier: Modifier,
    left: Boolean,
    session: SessionController,
    mods: KeyboardModifiers,
    keyH: Dp,
    font: TextUnit,
    gap: Dp,
) {
    Column(
        modifier = modifier.padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
    ) {
        if (left) {
            KeyRow(ROW1.take(6), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW2.take(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW3.take(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW4.take(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                KeyButton("CTRL", Modifier.weight(1.3f), keyH, font, mods.ctrl) { mods.ctrl = !mods.ctrl }
                KeyButton("SHIFT", Modifier.weight(1.3f), keyH, font, mods.shift) { mods.shift = !mods.shift }
                KeyButton("□", Modifier.weight(1f), keyH, font, mods.openApple) { mods.toggleOpenApple(session) }
                KeyButton("ESC", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_ESCAPE, 27, 0) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                KeyButton("TAB", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_TAB, 9, 0) }
                KeyButton("←", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_LEFT, 0, 0) }
                KeyButton("↓", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_DOWN, 0, 0) }
                KeyButton("SPACE", Modifier.weight(2f), keyH, font) { mods.space(session) }
            }
        } else {
            KeyRow(ROW1.drop(6), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW2.drop(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW3.drop(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            KeyRow(ROW4.drop(5), mods.shift, keyH, font, gap) { mods.emit(session, it.code, it.ascii, it.shiftAscii) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                KeyButton("■", Modifier.weight(1f), keyH, font, mods.closedApple) { mods.toggleClosedApple(session) }
                KeyButton("DEL", Modifier.weight(1.3f), keyH, font) { session.tapKey(Retro.K_DELETE, 127, 0) }
                KeyButton("RET", Modifier.weight(1.6f), keyH, font) { session.tapKey(Retro.K_RETURN, 13, 0) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                KeyButton("↑", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_UP, 0, 0) }
                KeyButton("→", Modifier.weight(1f), keyH, font) { session.tapKey(Retro.K_RIGHT, 0, 0) }
                KeyButton("RESET", Modifier.weight(1.6f), keyH, font, mods.ctrl) { mods.reset(session) }
            }
        }
    }
}

/** The gated key-press haptic pulse, supplied by the keyboard and fired by each [KeyButton]. */
private val LocalKeyHaptic = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
private fun KeyRow(keys: List<Key>, shift: Boolean, keyH: Dp, font: TextUnit, gap: Dp, onKey: (Key) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (k in keys) {
            KeyButton(if (shift) k.shiftFace else k.face, Modifier.weight(k.weight), keyH, font) { onKey(k) }
        }
    }
}

// Bright highlight for the D-pad / TV-remote focused key.
private val FocusAmber = Color(0xFFFFC107)

/**
 * A keyboard key. A height-controlled Box (not a Material Button, whose 40dp
 * minimum height blocks the compact TV layout). Active modifier keys invert to
 * make their sticky state obvious.
 *
 * On a TV the keys are driven by the remote's D-pad, so the focused key must read
 * clearly from across the room: it flips to a bright amber fill with a thick white
 * outline. (The platform's default focus tint is far too subtle on the red keys.)
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val haptic = LocalKeyHaptic.current
    val container = when {
        focused -> FocusAmber
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    val content = when {
        focused -> Color.Black
        active -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    Box(
        modifier = modifier
            .height(keyH)
            .clip(shape)
            .background(container)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = ripple()) { haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = font, color = content, textAlign = TextAlign.Center, maxLines = 1)
    }
}
