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

    @Test fun deleteKey() {
        val i = Interpreter(lib)
        i.tap()
        assertEquals(listOf<Emit>(Emit.Delete), i.delete().emits)
        assertEquals(false, i.punct)
    }

    @Test fun keyCodesExistForEveryTypableCharacter() {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + Alphabet.punctChar.values + listOf(' ', '.')
        for (c in chars) assertTrue(KeySender.keyFor(c) != null, "no key for '$c'")
    }
}
