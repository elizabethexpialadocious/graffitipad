package graffiti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

// Palm-style LCD palette
private val Chassis = Color(0xFF2B3029)
private val ChassisText = Color(0xFFD8DDCB)
private val ChassisDim = Color(0xFF8F9886)
private val Lcd = Color(0xFFB3C19E)
private val LcdSelected = Color(0xFFD4DEC2)
private val Ink = Color(0xFF1E281B)
private val Miss = Color(0xFF8E2F21)
private val StartDot = Color(0xFFB4532A)

/** Fraction of the pad width used for letters; the rest is the number area, as on a Palm. */
private const val SPLIT = 0.6f

/** Height of the guide, and the gap between rows; the window grows or shrinks by both together. */
private val GUIDE_HEIGHT = 220.dp
private val GAP = 8.dp

/**
 * Writing is the app's main job, so the pad never gets shorter than this. When the window is
 * made smaller, the guide shrinks first (it scrolls) and disappears below [GUIDE_MIN], then the
 * status line goes. The window can't be made smaller than what is left.
 */
private val PAD_MIN_HEIGHT = 160.dp
private val GUIDE_MIN = 56.dp
/** Narrow enough to tuck away, wide enough for the header and button rows. */
private val MIN_CONTENT_WIDTH = 520.dp
private val CONTENT_PADDING = 12.dp

fun main() = application {
    val state = rememberWindowState(width = 560.dp, height = 640.dp, position = WindowPosition(Alignment.BottomEnd))
    // focusable = false: clicking or drawing on the pad never takes keyboard focus away from
    // the app you are typing into, so the keys we press land there.
    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "Graffiti Pad",
        focusable = false,
        alwaysOnTop = true,
    ) {
        // Paint the native window in the chassis colour, so the strip uncovered while the
        // window grows is never drawn white for a frame.
        LaunchedEffect(Unit) {
            val bg = java.awt.Color(0x2B3029)
            window.background = bg
            window.contentPane.background = bg
        }
        MaterialTheme {
            GraffitiApp(
                onGuideShown = { shown, delta ->
                    // Grow or shrink the window by exactly the guide's height. The guide sits
                    // below the pad, so keeping the top-left corner fixed keeps the writing
                    // area in the same place and at the same size.
                    val loc = window.location
                    state.position = WindowPosition(loc.x.dp, loc.y.dp)
                    state.size = DpSize(state.size.width, state.size.height + if (shown) delta else -delta)
                },
                onMinContentSize = { min ->
                    // AWT window units are dp on Windows; add the title bar and borders.
                    val i = window.insets
                    val d = java.awt.Dimension(
                        Math.round(min.width.value) + i.left + i.right,
                        Math.round(min.height.value) + i.top + i.bottom,
                    )
                    if (window.minimumSize != d) window.minimumSize = d
                },
            )
        }
    }
}

@Composable
fun GraffitiApp(
    onGuideShown: (shown: Boolean, delta: Dp) -> Unit = { _, _ -> },
    onMinContentSize: (DpSize) -> Unit = {},
) {
    val library = remember { TemplateLibrary(TemplateLibrary.defaultFile()) }
    val interp = remember { Interpreter(library) }
    val sender = remember { KeySender() }
    val density = LocalDensity.current.density

    var shift by remember { mutableStateOf(ShiftState.OFF) }
    var punct by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    fun syncModes() { shift = interp.shift; punct = interp.punct; ctrl = interp.ctrl; alt = interp.alt }
    var lastShown by remember { mutableStateOf("") }
    var lastScore by remember { mutableStateOf<Double?>(null) }
    var lastOk by remember { mutableStateOf(true) }
    var lastInk by remember { mutableStateOf(listOf<Offset>()) }
    // The keys shown in the typed-text strip, kept as keys (not text) so Send can replay them
    // exactly: Enter as Enter, Ctrl+C as Ctrl+C.
    var typedKeys by remember { mutableStateOf(listOf<Emit>()) }
    val typed = typedKeys.joinToString("") { stripLabel(it) }
    var sending by remember { mutableStateOf(sender.available) }
    var showGuide by remember { mutableStateOf(true) }
    // While the window is being resized for the guide, lay out at the height the window is
    // about to have (from -> to), so the pad never stretches or squeezes for a frame.
    var resizeLock by remember { mutableStateOf<Pair<Dp, Dp>?>(null) }
    // Window height, and how tall the guide was last laid out (it shrinks in a small window).
    val measured = remember { object { var height = 0.dp; var guide = 0.dp } }
    fun setGuide(show: Boolean) {
        if (show == showGuide) return
        // Hiding removes the guide as it is now; showing brings it back at full height.
        val delta = if (show) GUIDE_HEIGHT + GAP else if (measured.guide > 0.dp) measured.guide + GAP else 0.dp
        val base = resizeLock?.second ?: measured.height
        resizeLock = measured.height to (base + if (show) delta else -delta)
        showGuide = show
        onGuideShown(show, delta)
    }
    var teachMode by remember { mutableStateOf(false) }
    var teachTarget by remember { mutableStateOf<Alphabet.Glyph?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var status by remember {
        mutableStateOf(
            if (sender.available) "Click in the app you want to type into, then write here or tap a key in the guide."
            else "Can't send keystrokes on this system; text only appears below."
        )
    }

    fun deliver(out: Interpreter.Outcome) {
        lastShown = out.shown
        lastScore = out.score
        lastOk = out.accepted
        syncModes()
        for (e in out.emits) {
            typedKeys = when (e) {
                Emit.Backspace -> typedKeys.dropLast(1)
                // The strip's cursor is always at the end, so there is nothing after it to
                // delete, and Esc doesn't type anything.
                Emit.Delete, Emit.Escape -> typedKeys
                // Characters, Enter, and shortcuts such as [Ctrl+c] are kept.
                else -> (typedKeys + e).takeLast(200)
            }
        }
        if (sending) sender.send(out.emits)
    }

    fun onStroke(raw: List<Offset>, zone: Zone) {
        val pts = raw.map { Pt(it.x.toDouble(), it.y.toDouble()) }
        val isTap = OneDollar.pathLength(pts) < Interpreter.TAP_PIXELS * density
        lastInk = raw
        val target = teachTarget
        if (teachMode) {
            if (target == null) { status = "Pick a shape in the guide first."; return }
            if (isTap) return
            library.teach(target.name, target.group, pts)
            version++
            lastOk = true
            status = "Saved example ${library.userCount(target.name, target.group)} for '${target.label}'. Draw more, or pick another shape."
            return
        }
        deliver(if (isTap) interp.tap() else interp.stroke(pts, zone))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Chassis)) {
    val windowHeight = maxHeight
    measured.height = windowHeight
    val lock = resizeLock
    // The window has reached its new size (or was resized another way): drop the lock.
    LaunchedEffect(windowHeight) {
        if (resizeLock?.let { it.first != windowHeight } == true) resizeLock = null
    }
    val contentHeight = if (lock != null && lock.first == windowHeight) lock.second else windowHeight
    PadFirstColumn(
        modifier = Modifier.fillMaxWidth().topAlignedHeight(contentHeight).padding(CONTENT_PADDING),
        onGuideHeight = { measured.guide = it },
        onMinHeight = { onMinContentSize(DpSize(MIN_CONTENT_WIDTH, it + CONTENT_PADDING * 2)) },
    ) {
        // ---- header: modes and last result
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GRAFFITI", color = ChassisText, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            ModeChip("CTRL", ctrl) { interp.toggleCtrl(); syncModes() }
            ModeChip("ALT", alt) { interp.toggleAlt(); syncModes() }
            ModeChip("SHIFT", shift == ShiftState.ONCE) { interp.toggleShift(); syncModes() }
            ModeChip("CAPS", shift == ShiftState.LOCK) { interp.toggleCaps(); syncModes() }
            ModeChip("PUNCT", punct) { interp.togglePunct(); syncModes() }
            Text(
                when {
                    lastShown.isEmpty() -> ""
                    lastScore == null -> lastShown
                    else -> "%s  %.2f".format(lastShown, lastScore)
                },
                color = if (lastOk) ChassisText else Color(0xFFE08A7A),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier.width(110.dp),
            )
        }
        Text(status, color = ChassisDim, fontSize = 12.sp, modifier = Modifier.layoutId(STATUS_ID))

        // ---- writing pad
        Pad(
            lastInk = lastInk,
            lastOk = lastOk,
            onStroke = ::onStroke,
            modifier = Modifier.fillMaxWidth().layoutId(PAD_ID),
        )

        // ---- Esc, what has been typed, then always-visible Backspace, Delete and Return keys
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadButton("Esc") { lastInk = emptyList(); deliver(interp.escape()) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1C201B)).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    typed.takeLast(56).ifEmpty { " " } + "▁",
                    color = ChassisText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            // Practice mode: type something here, then send it in one go. The strip is kept,
            // so the same text or command can be sent again.
            if (sender.available && !sending && typedKeys.isNotEmpty()) PadButton("Send", active = true) {
                sender.send(typedKeys)
                status = "Sent \"${typed.takeLast(40)}\" to the focused app. Send again, or Clear."
            }
            PadButton("⌫ Back") { lastInk = emptyList(); deliver(interp.press("backspace", Group.SPECIAL)) }
            PadButton("⌦ Del") { lastInk = emptyList(); deliver(interp.delete()) }
            PadButton("↵ Return") { lastInk = emptyList(); deliver(interp.press("enter", Group.SPECIAL)) }
        }

        // ---- controls
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PadButton(if (showGuide) "Hide guide" else "Guide") { setGuide(!showGuide) }
            PadButton(if (teachMode) "Done teaching" else "Teach", active = teachMode) {
                teachMode = !teachMode
                teachTarget = null
                confirmForget = false
                if (teachMode) {
                    setGuide(true)
                    status = "Teach mode: pick a shape in the guide, then draw it your way a few times."
                } else status = "Back to typing."
            }
            if (sender.available) PadButton(if (sending) "Keys: on" else "Keys: off", active = sending) {
                sending = !sending
                status = if (sending) "Sending keystrokes to the focused app." else "Practice mode: nothing is sent."
            }
            PadButton("Clear") {
                typedKeys = emptyList(); interp.reset(); syncModes(); lastShown = ""; lastInk = emptyList()
            }
            if (teachMode) PadButton(if (confirmForget) "Really forget?" else "Forget mine") {
                if (confirmForget) {
                    library.forgetUser(); version++; confirmForget = false
                    status = "Your examples were removed; only the built-in shapes remain."
                } else confirmForget = true
            }
        }

        if (showGuide) {
            Guide(
                selected = teachTarget,
                version = version,
                library = library,
                teachMode = teachMode,
                onPick = { g ->
                    if (teachMode) {
                        teachTarget = g
                        status = "Draw '${g.label}' on the pad (start at the orange dot). Each drawing is saved as an example."
                    } else {
                        lastInk = emptyList()
                        deliver(interp.press(g.name, g.group))
                    }
                },
                onTap = { if (!teachMode) { lastInk = emptyList(); deliver(interp.tap()) } },
                onForget = { g ->
                    library.forgetUser(g.name, g.group); version++
                    status = "Your examples of '${g.label}' were removed; the built-in shape remains."
                },
                modifier = Modifier.fillMaxWidth().layoutId(GUIDE_ID),
            )
        }
    }
    }
}

private const val PAD_ID = "pad"
private const val GUIDE_ID = "guide"
private const val STATUS_ID = "status"

/**
 * A top-to-bottom stack, like a Column with [GAP] between rows, that hands out height in order
 * of importance: first the pad gets [PAD_MIN_HEIGHT], then every plain row its natural height,
 * then the status line, then the guide (up to [GUIDE_HEIGHT]), and the pad takes whatever is
 * left over. Rows that don't fit (guide, then status) are left out rather than squeezing the pad.
 * Reports the smallest height that still shows the pad and plain rows, for the window minimum.
 */
@Composable
private fun PadFirstColumn(
    modifier: Modifier,
    onGuideHeight: (Dp) -> Unit,
    onMinHeight: (Dp) -> Unit,
    content: @Composable () -> Unit,
) {
    val guideCb by rememberUpdatedState(onGuideHeight)
    val minCb by rememberUpdatedState(onMinHeight)
    Layout(content, modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val gap = GAP.roundToPx()
        val padMin = PAD_MIN_HEIGHT.roundToPx()
        val natural = Constraints(maxWidth = w)
        val placeables = arrayOfNulls<Placeable>(measurables.size)

        // Plain rows (header, keys, controls) always show at their natural height.
        var fixed = 0
        var rows = 1 // the pad
        measurables.forEachIndexed { i, m ->
            if (m.layoutId == null) { val p = m.measure(natural); placeables[i] = p; fixed += p.height; rows++ }
        }
        minCb(((fixed + padMin + gap * (rows - 1)) / density).dp)

        var left = constraints.maxHeight - fixed - gap * (rows - 1) - padMin
        // Then the status line, if it fits.
        val statusIdx = measurables.indexOfFirst { it.layoutId == STATUS_ID }
        if (statusIdx >= 0) {
            val p = measurables[statusIdx].measure(natural)
            if (p.height + gap <= left) { placeables[statusIdx] = p; left -= p.height + gap }
        }
        // Then the guide, shrunk to fit, or left out if not even a row would show.
        val guideIdx = measurables.indexOfFirst { it.layoutId == GUIDE_ID }
        var guideH = 0
        if (guideIdx >= 0) {
            val h = minOf(GUIDE_HEIGHT.roundToPx(), left - gap)
            if (h >= GUIDE_MIN.roundToPx()) {
                placeables[guideIdx] = measurables[guideIdx].measure(Constraints.fixed(w, h))
                guideH = h; left -= h + gap
            }
        }
        guideCb((guideH / density).dp)
        // The pad takes everything that is left on top of its minimum.
        val padIdx = measurables.indexOfFirst { it.layoutId == PAD_ID }
        placeables[padIdx] = measurables[padIdx].measure(Constraints.fixed(w, padMin + maxOf(left, 0)))

        layout(w, constraints.maxHeight) {
            var y = 0
            for (p in placeables) {
                if (p == null) continue
                p.place(0, y)
                y += p.height + gap
            }
        }
    }
}

/** How a key looks in the typed-text strip: the character, ↵ for Enter, [Ctrl+c] for a shortcut. */
private fun stripLabel(e: Emit): String = when (e) {
    is Emit.Ch -> e.c.toString()
    Emit.Enter -> "↵"
    Emit.Backspace -> ""
    Emit.Delete -> ""
    Emit.Escape -> ""
    is Emit.Combo -> "[" + e.prefix + when (val k = e.key) {
        is Emit.Ch -> if (k.c == ' ') "Space" else k.c.toString()
        Emit.Backspace -> "Back"
        Emit.Delete -> "Del"
        Emit.Enter -> "Enter"
        Emit.Escape -> "Esc"
        is Emit.Combo -> ""
    } + "]"
}

/**
 * Lays the content out at exactly [height], pinned to the top, even if that is taller than
 * the window for a frame (the bottom is then cut off) or shorter (the background shows).
 */
private fun Modifier.topAlignedHeight(height: Dp) = layout { measurable, constraints ->
    val h = height.roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

@Composable
private fun Pad(lastInk: List<Offset>, lastOk: Boolean, onStroke: (List<Offset>, Zone) -> Unit, modifier: Modifier) {
    var current by remember { mutableStateOf(listOf<Offset>()) }
    val callback by rememberUpdatedState(onStroke)
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Lcd).border(2.dp, Color(0xFF151914), RoundedCornerShape(6.dp))) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val zone = if (down.position.x < size.width * SPLIT) Zone.LETTERS else Zone.NUMBERS
                    val pts = mutableListOf(down.position)
                    current = pts.toList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.position != pts.last()) pts += change.position
                        change.consume()
                        current = pts.toList()
                        if (!change.pressed) break
                    }
                    current = emptyList()
                    callback(pts.toList(), zone)
                }
            }
        ) {
            val x = size.width * SPLIT
            drawLine(
                Ink.copy(alpha = 0.3f), Offset(x, 10f), Offset(x, size.height - 10f),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            if (current.isEmpty() && lastInk.isNotEmpty())
                ink(lastInk, (if (lastOk) Ink else Miss).copy(alpha = 0.35f), 4.dp.toPx())
            ink(current, Ink, 4.dp.toPx())
        }
        Text("abc", color = Ink.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        Text("123", color = Ink.copy(alpha = 0.4f), fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

private fun DrawScope.ink(pts: List<Offset>, color: Color, width: Float) {
    if (pts.isEmpty()) return
    if (pts.size == 1) { drawCircle(color, width / 2, pts[0]); return }
    val p = Path()
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
    drawPath(p, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Guide(
    selected: Alphabet.Glyph?,
    version: Int,
    library: TemplateLibrary,
    teachMode: Boolean,
    onPick: (Alphabet.Glyph) -> Unit,
    onTap: () -> Unit,
    onForget: (Alphabet.Glyph) -> Unit,
    modifier: Modifier,
) {
    val grid = rememberLazyGridState()
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp),
            state = grid,
            // Leave room for the scrollbar, and let pen, touch or mouse drag the guide up and
            // down. A drag past the touch slop cancels the key press underneath it.
            modifier = Modifier.fillMaxSize().padding(end = 12.dp).pointerInput(grid) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    grid.dispatchRawDelta(-dy)
                }
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(Alphabet.all) { g ->
                val mine = if (version >= 0) library.userCount(g.name, g.group) else 0
                GlyphCell(g, g === selected, mine, onForget = { onForget(g) }) { onPick(g) }
            }
            // Tap isn't a shape you can teach, but as a key it is handy: once for
            // punctuation mode, twice for a period.
            if (!teachMode) item { TapCell(onTap) }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(grid),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = ScrollbarStyle(
                minimalHeight = 24.dp,
                thickness = 8.dp,
                shape = RoundedCornerShape(4.dp),
                hoverDurationMillis = 300,
                unhoverColor = ChassisDim.copy(alpha = 0.5f),
                hoverColor = ChassisText.copy(alpha = 0.8f),
            ),
        )
    }
}

@Composable
private fun TapCell(onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(4.dp)).background(Lcd).clickable { onClick() }.padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(28.dp)) { drawCircle(StartDot, 2.5.dp.toPx(), center) }
        Text("tap", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text("punct / .", color = Ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
    }
}

/**
 * One key in the guide. Click types it (or picks it in teach mode); right-click or long-press
 * opens a menu for forgetting the examples you taught for this shape.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphCell(g: Alphabet.Glyph, selected: Boolean, mine: Int, onForget: () -> Unit, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val area = when (g.group) {
        Group.SPECIAL -> "any"
        Group.LETTER -> "abc"
        Group.DIGIT -> "123"
        Group.PUNCT -> "tap+"
    }
    Column(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(if (selected) LcdSelected else Lcd)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val e = awaitPointerEvent()
                    if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                        e.changes.forEach { it.consume() }
                        menu = true
                    }
                }
            }
            .combinedClickable(onLongClick = { menu = true }) { onClick() }
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (mine > 0) "Forget my $mine example${if (mine == 1) "" else "s"} of '${g.label}'"
                        else "No examples of your own for '${g.label}'",
                        fontSize = 12.sp,
                    )
                },
                enabled = mine > 0,
                onClick = { menu = false; onForget() },
            )
        }
        Canvas(Modifier.size(28.dp)) { glyph(g.shape) }
        Text(
            g.label + if (mine > 0) " +$mine" else "",
            color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        )
        Text(area, color = Ink.copy(alpha = 0.5f), fontSize = 8.sp, maxLines = 1)
    }
}

/** Draws a template shape fitted into the cell, with a dot where the stroke starts. */
private fun DrawScope.glyph(shape: List<Pt>) {
    val minX = shape.minOf { it.x }; val maxX = shape.maxOf { it.x }
    val minY = shape.minOf { it.y }; val maxY = shape.maxOf { it.y }
    val pad = 3.5.dp.toPx()
    val bw = maxOf(maxX - minX, 1e-6); val bh = maxOf(maxY - minY, 1e-6)
    val k = minOf((size.width - 2 * pad) / bw, (size.height - 2 * pad) / bh)
    val ox = (size.width - bw * k) / 2 - minX * k
    val oy = (size.height - bh * k) / 2 - minY * k
    val pts = shape.map { Offset((ox + it.x * k).toFloat(), (oy + it.y * k).toFloat()) }
    ink(pts, Ink, 1.5.dp.toPx())
    drawCircle(StartDot, 2.5.dp.toPx(), pts.first())
}

@Composable
private fun ModeChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (on) Chassis else ChassisDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .background(if (on) Lcd else Color.Transparent)
            .border(1.dp, if (on) Lcd else ChassisDim.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun PadButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Lcd else Color(0xFF454C41),
            contentColor = if (active) Chassis else ChassisText,
        ),
        shape = RoundedCornerShape(5.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, fontSize = 12.sp) }
}
