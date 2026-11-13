package graffiti

import java.awt.Robot
import java.awt.event.KeyEvent
import java.util.concurrent.Executors

/**
 * Presses real keys with java.awt.Robot, so text lands in whichever window has keyboard
 * focus. The pad window itself never takes focus (see Main.kt), so that is the app the
 * user was typing in. Key codes follow the US keyboard layout.
 */
class KeySender {
    private val robot: Robot? = runCatching { Robot().apply { autoDelay = 4 } }.getOrNull()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "keys").apply { isDaemon = true } }

    val available get() = robot != null

    fun send(emits: List<Emit>) {
        val r = robot ?: return
        if (emits.isEmpty()) return
        worker.execute { emits.forEach { e -> press(r, e) } }
    }

    private fun press(r: Robot, e: Emit) {
        val mods = ArrayList<Int>()
        var key = e
        if (key is Emit.Combo) {
            if (key.ctrl) mods += KeyEvent.VK_CONTROL
            if (key.alt) mods += KeyEvent.VK_ALT
            key = key.key
        }
        val (vk, shift) = when (key) {
            Emit.Backspace -> KeyEvent.VK_BACK_SPACE to false
            Emit.Delete -> KeyEvent.VK_DELETE to false
            Emit.Enter -> KeyEvent.VK_ENTER to false
            Emit.Escape -> KeyEvent.VK_ESCAPE to false
            is Emit.Ch -> keyFor(key.c) ?: return
            is Emit.Combo -> return
        }
        if (shift) mods += KeyEvent.VK_SHIFT
        tap(r, vk, mods)
    }

    /** Holds [mods] down, presses and releases [vk], then lets go of [mods] in reverse order. */
    private fun tap(r: Robot, vk: Int, mods: List<Int>) {
        val held = ArrayList<Int>()
        try {
            for (m in mods) { r.keyPress(m); held += m }
            r.keyPress(vk)
            r.keyRelease(vk)
        } finally {
            for (m in held.asReversed()) r.keyRelease(m)
        }
    }

    companion object {
        /** Key code and whether Shift is held, for a US layout. */
        fun keyFor(c: Char): Pair<Int, Boolean>? = when (c) {
            in 'a'..'z' -> (KeyEvent.VK_A + (c - 'a')) to false
            in 'A'..'Z' -> (KeyEvent.VK_A + (c - 'A')) to true
            in '0'..'9' -> (KeyEvent.VK_0 + (c - '0')) to false
            ' ' -> KeyEvent.VK_SPACE to false
            '.' -> KeyEvent.VK_PERIOD to false
            ',' -> KeyEvent.VK_COMMA to false
            '-' -> KeyEvent.VK_MINUS to false
            '/' -> KeyEvent.VK_SLASH to false
            '\'' -> KeyEvent.VK_QUOTE to false
            '?' -> KeyEvent.VK_SLASH to true
            '!' -> KeyEvent.VK_1 to true
            '(' -> KeyEvent.VK_9 to true
            ')' -> KeyEvent.VK_0 to true
            else -> null
        }
    }
}
