package graffiti

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecognizerTest {
    private val lib = TemplateLibrary(null)

    private fun wobble(shape: List<Pt>, r: Random): List<Pt> {
        val rot = Math.toRadians(r.nextDouble() * 12 - 6)
        val size = 80 + r.nextDouble() * 100
        return OneDollar.resample(shape, 120).map {
            val x = it.x + 0.02 * r.nextDouble(); val y = it.y + 0.02 * r.nextDouble()
            Pt((x * cos(rot) - y * sin(rot)) * size, (x * sin(rot) + y * cos(rot)) * size)
        }
    }

    @Test fun everyBuiltInShapeIsRecognizedWhenDrawnSloppily() {
        val r = Random(7)
        var ok = 0; var n = 0
        for (g in Alphabet.all) repeat(20) {
            val interp = Interpreter(lib, 0.0)
            if (g.group == Group.PUNCT) interp.tap()
            val zone = if (g.group == Group.DIGIT) Zone.NUMBERS else Zone.LETTERS
            val c = interp.candidates(zone)
            val stroke = wobble(g.shape, r)
            val best = OneDollar.matchLine(stroke, c) ?: OneDollar.recognize(stroke, c).first()
            n++
            if (best.template.name == g.name) ok++
        }
        assertTrue(ok >= n * 0.97, "only $ok of $n recognized")
    }

    @Test fun shiftCapsAndPunctuation() {
        val i = Interpreter(lib)
        fun draw(name: String, group: Group, zone: Zone = Zone.LETTERS) =
            i.stroke(Alphabet.glyph(name, group)!!.shape.map { Pt(it.x * 100, it.y * 100) }, zone).emits

        assertEquals(listOf<Emit>(Emit.Ch('a')), draw("a", Group.LETTER))
        draw("shift", Group.SPECIAL)
        assertEquals(listOf<Emit>(Emit.Ch('B')), draw("b", Group.LETTER))
        assertEquals(listOf<Emit>(Emit.Ch('c')), draw("c", Group.LETTER))
        draw("shift", Group.SPECIAL); draw("shift", Group.SPECIAL)
        assertEquals(ShiftState.LOCK, i.shift)
        assertEquals(listOf<Emit>(Emit.Ch('L')), draw("l", Group.LETTER))
        assertEquals(listOf<Emit>(Emit.Ch('7')), draw("7", Group.DIGIT, Zone.NUMBERS))
        assertEquals(listOf<Emit>(Emit.Ch(' ')), draw("space", Group.SPECIAL))
        assertEquals(listOf<Emit>(Emit.Backspace), draw("backspace", Group.SPECIAL))
        i.tap()
        assertEquals(listOf<Emit>(Emit.Ch(',')), draw("comma", Group.PUNCT))
        i.tap()
        assertEquals(listOf<Emit>(Emit.Ch('.')), i.tap().emits)
    }

    @Test fun guideKeysTypeLikeStrokes() {
        val i = Interpreter(lib)
        assertEquals(listOf<Emit>(Emit.Ch('a')), i.press("a", Group.LETTER).emits)
        i.press("shift", Group.SPECIAL)
        assertEquals(ShiftState.ONCE, i.shift)
        assertEquals(listOf<Emit>(Emit.Ch('Q')), i.press("q", Group.LETTER).emits)
        assertEquals(ShiftState.OFF, i.shift)
        assertEquals(listOf<Emit>(Emit.Ch('4')), i.press("4", Group.DIGIT).emits)
        assertEquals(listOf<Emit>(Emit.Enter), i.press("enter", Group.SPECIAL).emits)
        // punctuation keys work without a tap first, and a letter key leaves punctuation mode
        assertEquals(listOf<Emit>(Emit.Ch('?')), i.press("question", Group.PUNCT).emits)
        i.tap()
        assertEquals(listOf<Emit>(Emit.Ch('z')), i.press("z", Group.LETTER).emits)
        assertEquals(false, i.punct)
    }

    @Test fun modeChipsToggle() {
        val i = Interpreter(lib)
        i.toggleShift(); assertEquals(ShiftState.ONCE, i.shift)
        assertEquals(listOf<Emit>(Emit.Ch('A')), i.press("a", Group.LETTER).emits)
        assertEquals(ShiftState.OFF, i.shift)
        i.toggleCaps(); assertEquals(ShiftState.LOCK, i.shift)
        i.toggleShift(); assertEquals(ShiftState.ONCE, i.shift)
        i.toggleShift(); assertEquals(ShiftState.OFF, i.shift)
        i.toggleCaps(); i.toggleCaps(); assertEquals(ShiftState.OFF, i.shift)
        i.togglePunct(); assertEquals(true, i.punct)
        i.togglePunct(); assertEquals(false, i.punct)
    }

    @Test fun deleteAndEscapeKeys() {
        val i = Interpreter(lib)
        i.tap()
        assertEquals(listOf<Emit>(Emit.Delete), i.delete().emits)
        assertEquals(false, i.punct)
        i.tap()
        assertEquals(listOf<Emit>(Emit.Escape), i.escape().emits)
        assertEquals(false, i.punct)
        i.toggleCtrl()
        assertEquals(listOf<Emit>(Emit.Combo(Emit.Escape, ctrl = true, alt = false)), i.escape().emits)
    }

    @Test fun ctrlAndAltApplyToTheNextKeyOnly() {
        val i = Interpreter(lib)
        fun combo(k: Emit, ctrl: Boolean = true, alt: Boolean = false) = listOf<Emit>(Emit.Combo(k, ctrl, alt))
        i.toggleCtrl()
        assertEquals(combo(Emit.Ch('c')), i.press("c", Group.LETTER).emits)
        assertEquals(false, i.ctrl)
        assertEquals(listOf<Emit>(Emit.Ch('v')), i.press("v", Group.LETTER).emits)
        // the Shift stroke doesn't use up Ctrl; they combine into Ctrl+Shift
        i.toggleCtrl(); i.press("shift", Group.SPECIAL)
        assertEquals(true, i.ctrl)
        assertEquals(combo(Emit.Ch('Z')), i.press("z", Group.LETTER).emits)
        // Caps Lock doesn't add Shift to a shortcut
        i.toggleCaps(); i.toggleCtrl()
        assertEquals(combo(Emit.Ch('s')), i.press("s", Group.LETTER).emits)
        i.toggleCaps()
        // Ctrl+Alt together, and Alt alone, on special keys
        i.toggleCtrl(); i.toggleAlt()
        assertEquals(combo(Emit.Delete, alt = true), i.delete().emits)
        i.toggleAlt()
        assertEquals(combo(Emit.Backspace, ctrl = false, alt = true), i.press("backspace", Group.SPECIAL).emits)
        // toggling twice cancels
        i.toggleCtrl(); i.toggleCtrl()
        assertEquals(listOf<Emit>(Emit.Enter), i.press("enter", Group.SPECIAL).emits)
    }

    @Test fun keyCodesExistForEveryTypableCharacter() {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + Alphabet.punctChar.values + listOf(' ', '.')
        for (c in chars) assertTrue(KeySender.keyFor(c) != null, "no key for '$c'")
    }

    @Test fun forgettingOneShapeKeepsTheOthers() {
        val l = TemplateLibrary(null)
        val a = Alphabet.all.first { it.group == Group.LETTER }
        val b = Alphabet.all.last { it.group == Group.LETTER }
        repeat(2) { l.teach(a.name, a.group, a.shape) }
        l.teach(b.name, b.group, b.shape)
        l.forgetUser(a.name, a.group)
        assertEquals(0, l.userCount(a.name, a.group))
        assertEquals(1, l.userCount(b.name, b.group))
    }
}
