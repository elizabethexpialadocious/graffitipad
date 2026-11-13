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
        when (e) {
            Emit.Backspace -> tap(r, KeyEvent.VK_BACK_SPACE, false)
            Emit.Delete -> tap(r, KeyEvent.VK_DELETE, false)
            Emit.Enter -> tap(r, KeyEvent.VK_ENTER, false)
            is Emit.Ch -> keyFor(e.c)?.let { (vk, shift) -> tap(r, vk, shift) }
        }
    }

    private fun tap(r: Robot, vk: Int, shift: Boolean) {
        if (shift) r.keyPress(KeyEvent.VK_SHIFT)
        try {
            r.keyPress(vk)
            r.keyRelease(vk)
        } finally {
            if (shift) r.keyRelease(KeyEvent.VK_SHIFT)
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
