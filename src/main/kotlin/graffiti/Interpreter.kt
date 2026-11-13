package graffiti

import java.io.File

/** Something to send to the keyboard. */
sealed interface Emit {
    data class Ch(val c: Char) : Emit
    data object Backspace : Emit
    /** Forward delete: removes the character after the cursor. */
    data object Delete : Emit
    data object Enter : Emit
    data object Escape : Emit
    /** [key] pressed while holding Ctrl and/or Alt, e.g. Ctrl+C or Ctrl+Backspace. */
    data class Combo(val key: Emit, val ctrl: Boolean, val alt: Boolean) : Emit {
        /** "Ctrl+Alt+" etc., for showing on screen. */
        val prefix get() = (if (ctrl) "Ctrl+" else "") + (if (alt) "Alt+" else "")
    }
}

enum class Zone { LETTERS, NUMBERS }

enum class ShiftState { OFF, ONCE, LOCK }

/** Built-in templates plus the ones the user taught, saved to a text file. */
class TemplateLibrary(private val file: File?) {
    private val builtIn = Alphabet.builtInTemplates()
    private val user = ArrayList<Template>()

    init { load() }

    val templates: List<Template> get() = builtIn + user

    fun userCount(name: String, group: Group) = user.count { it.name == name && it.group == group }

    fun teach(name: String, group: Group, stroke: List<Pt>) {
        user += Template(name, group, OneDollar.normalize(stroke), user = true)
        save()
    }

    fun forgetUser() { user.clear(); save() }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        f.readLines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@forEach
            val group = runCatching { Group.valueOf(parts[0]) }.getOrNull() ?: return@forEach
            val pts = parts[2].trim().split(' ').mapNotNull { xy ->
                val c = xy.split(',')
                if (c.size == 2) Pt(c[0].toDoubleOrNull() ?: return@mapNotNull null, c[1].toDoubleOrNull() ?: return@mapNotNull null) else null
            }
            if (pts.size == OneDollar.N) user += Template(parts[1], group, pts, user = true)
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.writeText(user.joinToString("") { t ->
                t.group.name + "\t" + t.name + "\t" + t.points.joinToString(" ") { "%.2f,%.2f".format(java.util.Locale.ROOT, it.x, it.y) } + "\n"
            })
        }
    }

    companion object {
        fun defaultFile(): File {
            val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config")
            return File(base, "GraffitiPad/templates.txt")
        }
    }
}

/**
 * Turns strokes into key presses, keeping Graffiti's modes:
 *  - up stroke = Shift (next letter capital); a second up stroke = Caps Lock; a third = off
 *  - tap = punctuation shift; the next stroke is read as punctuation, and a second tap types "."
 */
class Interpreter(val library: TemplateLibrary, var minScore: Double = DEFAULT_MIN_SCORE) {
    var shift = ShiftState.OFF; private set
    var punct = false; private set
    /** One-shot modifiers: held down for the next key that types something, then released. */
    var ctrl = false; private set
    var alt = false; private set

    data class Outcome(val emits: List<Emit>, val shown: String, val score: Double?, val accepted: Boolean)

    fun reset() { shift = ShiftState.OFF; punct = false; ctrl = false; alt = false }

    /** Mode chips in the header: each one turns its mode on, or back off if it was on. */
    fun toggleShift() { shift = if (shift == ShiftState.ONCE) ShiftState.OFF else ShiftState.ONCE }
    fun toggleCaps() { shift = if (shift == ShiftState.LOCK) ShiftState.OFF else ShiftState.LOCK }
    fun togglePunct() { punct = !punct }
    fun toggleCtrl() { ctrl = !ctrl }
    fun toggleAlt() { alt = !alt }

    /** Wraps the key in an outcome with Ctrl/Alt if either is armed, then disarms them. */
    private fun withModifiers(o: Outcome): Outcome {
        if (!(ctrl || alt) || o.emits.isEmpty()) return o
        val combo = Emit.Combo(o.emits[0], ctrl, alt)
        val out = Outcome(listOf(combo) + o.emits.drop(1), combo.prefix + o.shown, o.score, o.accepted)
        ctrl = false; alt = false
        return out
    }

    /** The Delete and Esc keys on the button row. Like other non-punctuation keys, they leave punctuation mode. */
    fun delete(): Outcome = buttonKey(Emit.Delete, "delete")
    fun escape(): Outcome = buttonKey(Emit.Escape, "esc")

    private fun buttonKey(e: Emit, shown: String): Outcome {
        punct = false
        return withModifiers(Outcome(listOf(e), shown, null, true))
    }

    fun candidates(zone: Zone): List<Template> = library.templates.filter {
        if (punct) it.group == Group.PUNCT || (it.group == Group.SPECIAL && it.name == "backspace")
        else it.group == Group.SPECIAL || it.group == (if (zone == Zone.LETTERS) Group.LETTER else Group.DIGIT)
    }

    fun tap(): Outcome {
        return if (punct) {
            punct = false
            withModifiers(Outcome(listOf(Emit.Ch('.')), ".", null, true))
        } else {
            punct = true
            Outcome(emptyList(), "punctuation…", null, true)
        }
    }

    fun stroke(points: List<Pt>, zone: Zone): Outcome {
        val cands = candidates(zone)
        val best = OneDollar.matchLine(points, cands) ?: OneDollar.recognize(points, cands).firstOrNull()
            ?: return Outcome(emptyList(), "?", null, false)
        if (best.score < minScore) {
            punct = false
            return Outcome(emptyList(), "?", best.score, false)
        }
        return apply(best.template.name, best.template.group, best.score)
    }

    /**
     * A key pressed on the on-screen guide instead of drawn. Behaves like a perfectly
     * recognized stroke, so Shift and Caps Lock still apply. Punctuation keys type directly;
     * any other key except backspace leaves punctuation mode first.
     */
    fun press(name: String, group: Group): Outcome {
        if (group != Group.PUNCT && name != "backspace") punct = false
        return apply(name, group, null)
    }

    private fun apply(name: String, group: Group, score: Double?): Outcome {
        val emits = ArrayList<Emit>()
        var shown: String
        when {
            group == Group.PUNCT -> {
                val c = Alphabet.punctChar[name] ?: '?'
                emits += Emit.Ch(c); shown = c.toString(); punct = false
            }
            name == "backspace" && punct -> { punct = false; shown = "cancel" }
            name == "backspace" -> { emits += Emit.Backspace; shown = "back" }
            name == "space" -> { emits += Emit.Ch(' '); shown = "space" }
            name == "enter" -> { emits += Emit.Enter; shown = "enter" }
            name == "shift" -> {
                shift = when (shift) { ShiftState.OFF -> ShiftState.ONCE; ShiftState.ONCE -> ShiftState.LOCK; ShiftState.LOCK -> ShiftState.OFF }
                shown = when (shift) { ShiftState.OFF -> "shift off"; ShiftState.ONCE -> "shift"; ShiftState.LOCK -> "caps lock" }
            }
            group == Group.LETTER -> {
                // Like a real keyboard, Caps Lock doesn't turn Ctrl+C into Ctrl+Shift+C; Shift does.
                val upper = if (ctrl || alt) shift == ShiftState.ONCE else shift != ShiftState.OFF
                val c = if (upper) name[0].uppercaseChar() else name[0]
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
                emits += Emit.Ch(c); shown = c.toString()
            }
            else -> { emits += Emit.Ch(name[0]); shown = name }
        }
        return withModifiers(Outcome(emits, shown, score, true))
    }

    companion object {
        const val DEFAULT_MIN_SCORE = 0.75
        /** Strokes shorter than this (in pixels) count as a tap. */
        const val TAP_PIXELS = 8.0
    }
}
