package graffiti

import kotlin.math.cos
import kotlin.math.sin

/**
 * The built-in Graffiti shapes, drawn in a unit box (x right, y down, (0,0) top-left).
 * Every shape is a single stroke and its starting point and direction matter.
 */
object Alphabet {

    class Glyph(val name: String, val group: Group, val label: String, val shape: List<Pt>)

    private class PathBuilder {
        val pts = ArrayList<Pt>()
        fun to(x: Double, y: Double) { pts += Pt(x, y) }
        /** Elliptical arc; angles in degrees, 0 = right, 90 = bottom (screen coordinates). */
        fun arc(cx: Double, cy: Double, rx: Double, ry: Double, fromDeg: Double, toDeg: Double) {
            val steps = 48
            for (k in 0..steps) {
                val a = Math.toRadians(fromDeg + (toDeg - fromDeg) * k / steps)
                pts += Pt(cx + rx * cos(a), cy + ry * sin(a))
            }
        }
    }

    private fun shape(block: PathBuilder.() -> Unit): List<Pt> = PathBuilder().apply(block).pts

    private val glyphs = ArrayList<Glyph>()
    private fun g(name: String, group: Group, label: String = name, block: PathBuilder.() -> Unit) {
        glyphs += Glyph(name, group, label, shape(block))
    }

    // Shapes shared by several places
    private val sTop: PathBuilder.() -> Unit = { arc(.5, .25, .5, .25, -20.0, -270.0) }
    private val sBottom: PathBuilder.() -> Unit = { arc(.5, .75, .5, .25, -90.0, 160.0) }

    init {
        // ---- strokes that work anywhere on the pad
        g("space", Group.SPECIAL, "space") { to(0.0, .5); to(1.0, .5) }
        g("backspace", Group.SPECIAL, "back") { to(1.0, .5); to(0.0, .5) }
        g("enter", Group.SPECIAL, "enter") { to(1.0, 0.0); to(0.0, 1.0) }
        g("shift", Group.SPECIAL, "shift") { to(.5, 1.0); to(.5, 0.0) }

        // ---- letters (left side of the pad)
        g("a", Group.LETTER) { to(0.0, 1.0); to(.5, 0.0); to(1.0, 1.0) }
        g("b", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(0.0, 0.0); arc(0.0, .25, .5, .25, -90.0, 90.0); arc(0.0, .75, .55, .25, -90.0, 90.0) }
        g("c", Group.LETTER) { arc(.5, .5, .5, .5, -45.0, -315.0) }
        g("d", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(0.0, 0.0); arc(0.0, .5, .8, .5, -90.0, 90.0) }
        g("e", Group.LETTER) { arc(.5, .25, .5, .25, -40.0, -270.0); arc(.5, .75, .5, .25, -90.0, -320.0) }
        g("f", Group.LETTER) { to(1.0, 0.0); to(0.0, 0.0); to(0.0, 1.0) }
        g("g", Group.LETTER) { arc(.5, .5, .5, .5, -45.0, -360.0); to(.55, .5) }
        g("h", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(0.0, .7); arc(.45, .7, .45, .25, 180.0, 360.0); to(.9, 1.0) }
        g("i", Group.LETTER) { to(.5, 0.0); to(.5, 1.0) }
        g("j", Group.LETTER) { to(1.0, 0.0); to(1.0, .65); arc(.55, .65, .45, .35, 0.0, 180.0) }
        g("k", Group.LETTER) { to(1.0, 0.0); to(0.0, .5); to(1.0, 1.0) }
        g("l", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(.8, 1.0) }
        g("m", Group.LETTER) { to(0.0, 1.0); to(.15, 0.0); to(.5, .65); to(.85, 0.0); to(1.0, 1.0) }
        g("n", Group.LETTER) { to(0.0, 1.0); to(0.0, 0.0); to(1.0, 1.0); to(1.0, 0.0) }
        g("o", Group.LETTER) { arc(.5, .5, .5, .5, -90.0, -450.0) }
        g("p", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(0.0, 0.0); arc(0.0, .27, .6, .27, -90.0, 90.0) }
        g("q", Group.LETTER) { arc(.5, .45, .5, .45, -90.0, -450.0); to(1.0, 1.0) }
        g("r", Group.LETTER) { to(0.0, 0.0); to(0.0, 1.0); to(0.0, 0.0); arc(0.0, .27, .6, .27, -90.0, 90.0); to(.75, 1.0) }
        g("s", Group.LETTER) { sTop(); sBottom() }
        g("t", Group.LETTER) { to(0.0, 0.0); to(1.0, 0.0); to(1.0, 1.0) }
        g("u", Group.LETTER) { to(0.0, 0.0); to(0.0, .6); arc(.5, .6, .5, .4, 180.0, 0.0); to(1.0, 0.0) }
        g("v", Group.LETTER) { to(0.0, 0.0); to(.5, 1.0); to(1.0, 0.0) }
        g("w", Group.LETTER) { to(0.0, 0.0); to(.25, 1.0); to(.5, .35); to(.75, 1.0); to(1.0, 0.0) }
        g("x", Group.LETTER) { to(0.0, 0.0); to(1.0, 1.0); to(1.0, 0.0); to(0.0, 1.0) }
        g("y", Group.LETTER) { to(0.0, 0.0); to(.45, .45); to(1.0, 0.0); to(.25, 1.0) }
        g("z", Group.LETTER) { to(0.0, 0.0); to(1.0, 0.0); to(0.0, 1.0); to(1.0, 1.0) }

        // ---- digits (right side of the pad)
        g("0", Group.DIGIT) { arc(.5, .5, .4, .5, -90.0, -450.0) }
        g("1", Group.DIGIT) { to(.5, 0.0); to(.5, 1.0) }
        g("2", Group.DIGIT) { arc(.5, .3, .5, .3, 200.0, 380.0); to(0.0, 1.0); to(1.0, 1.0) }
        g("3", Group.DIGIT) { arc(.5, .25, .5, .25, 200.0, 450.0); arc(.5, .75, .5, .25, -90.0, 160.0) }
        g("4", Group.DIGIT) { to(0.0, 0.0); to(0.0, .65); to(1.0, .65) }
        g("5", Group.DIGIT) { to(1.0, 0.0); to(.1, 0.0); to(.1, .45); arc(.45, .72, .5, .3, -130.0, 150.0) }
        g("6", Group.DIGIT) { to(.85, 0.0); arc(.5, .7, .4, .3, 180.0, -170.0) }
        g("7", Group.DIGIT) { to(0.0, 0.0); to(1.0, 0.0); to(.35, 1.0) }
        g("8", Group.DIGIT) { sTop(); sBottom(); to(.9, .05) }
        g("9", Group.DIGIT) { arc(.5, .3, .45, .3, 0.0, -360.0); to(.95, 1.0) }

        // ---- punctuation (after one tap; a second tap types a period)
        g("comma", Group.PUNCT, ",") { to(1.0, 0.0); to(0.0, 1.0) }
        g("question", Group.PUNCT, "?") { arc(.5, .3, .5, .3, 200.0, 450.0); to(.5, 1.0) }
        g("exclaim", Group.PUNCT, "!") { to(.5, 0.0); to(.5, 1.0) }
        g("dash", Group.PUNCT, "-") { to(0.0, .5); to(1.0, .5) }
        g("apostrophe", Group.PUNCT, "'") { to(.5, 1.0); to(.5, 0.0) }
        g("lparen", Group.PUNCT, "(") { arc(1.0, .5, 1.0, .5, -90.0, -270.0) }
        g("rparen", Group.PUNCT, ")") { arc(0.0, .5, 1.0, .5, -90.0, 90.0) }
        g("slash", Group.PUNCT, "/") { to(0.0, 1.0); to(1.0, 0.0) }
    }

    val all: List<Glyph> get() = glyphs

    fun glyph(name: String, group: Group): Glyph? = glyphs.firstOrNull { it.name == name && it.group == group }

    fun builtInTemplates(): List<Template> = glyphs.map {
        val line = if (it.shape.size == 2) {
            val (a, b) = it.shape
            Math.toDegrees(kotlin.math.atan2(b.y - a.y, b.x - a.x))
        } else null
        Template(it.name, it.group, OneDollar.normalize(it.shape), lineAngle = line)
    }

    /** What each punctuation gesture types. */
    val punctChar = mapOf(
        "comma" to ',', "question" to '?', "exclaim" to '!', "dash" to '-',
        "apostrophe" to '\'', "lparen" to '(', "rparen" to ')', "slash" to '/',
    )
}
