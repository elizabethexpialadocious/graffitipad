package graffiti

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in screen coordinates (y grows downward). */
data class Pt(val x: Double, val y: Double)

/** Which family a gesture belongs to; decides where on the pad it can be written. */
enum class Group { SPECIAL, LETTER, DIGIT, PUNCT }

/** A stored example of one gesture, already normalized by [OneDollar.normalize]. */
class Template(
    val name: String,
    val group: Group,
    val points: List<Pt>,
    val user: Boolean = false,
    /** For straight-line gestures: direction in degrees (0 = right, 90 = down). */
    val lineAngle: Double? = null,
)

data class Match(val template: Template, val score: Double)

/**
 * The $1 Unistroke Recognizer (Wobbrock, Wilson & Li, UIST 2007).
 *
 * Steps: resample the stroke to 64 evenly spaced points, scale, move its centroid to the
 * origin, then compare it point-by-point with each template, searching for the best small
 * rotation with golden-section search. The template with the smallest average distance wins.
 *
 * Two changes from the paper, both because Graffiti depends on orientation and proportions:
 *  - no rotation to the "indicative angle": a down stroke (I) and an up stroke (Shift), or
 *    a right stroke (Space) and a left stroke (Backspace), must stay different. Only a
 *    +-[angleRangeDeg] wiggle is searched, to tolerate a tilted hand.
 *  - uniform scaling (by the longer side) instead of squashing into a square, so straight
 *    lines work and tall shapes stay tall.
 */
object OneDollar {
    const val N = 64
    const val SIZE = 250.0
    private val HALF_DIAGONAL = 0.5 * sqrt(2 * SIZE * SIZE)
    private val PHI = 0.5 * (-1.0 + sqrt(5.0))
    private val PRECISION = Math.toRadians(2.0)
    var angleRangeDeg = 20.0

    fun normalize(raw: List<Pt>): List<Pt> {
        require(raw.isNotEmpty()) { "empty stroke" }
        val r = resample(raw, N)
        return translateToOrigin(scaleUniform(r, SIZE))
    }

    /** All candidates, best first. Score is 1 for a perfect match and falls toward 0. */
    fun recognize(raw: List<Pt>, templates: List<Template>): List<Match> {
        if (templates.isEmpty()) return emptyList()
        val pts = normalize(raw)
        val range = Math.toRadians(angleRangeDeg)
        return templates
            .map { t -> Match(t, 1.0 - distanceAtBestAngle(pts, t.points, -range, range) / HALF_DIAGONAL) }
            .sortedByDescending { it.score }
    }

    fun pathLength(p: List<Pt>): Double {
        var d = 0.0
        for (i in 1 until p.size) d += dist(p[i - 1], p[i])
        return d
    }

    fun resample(points: List<Pt>, n: Int): List<Pt> {
        val total = pathLength(points)
        if (total <= 0.0) return List(n) { points[0] }
        val interval = total / (n - 1)
        val src = points.toMutableList()
        val out = ArrayList<Pt>(n)
        out.add(src[0])
        var acc = 0.0
        var i = 1
        while (i < src.size) {
            val a = src[i - 1]
            val b = src[i]
            val seg = dist(a, b)
            if (seg > 0 && acc + seg >= interval) {
                val t = (interval - acc) / seg
                val q = Pt(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                out.add(q)
                src.add(i, q) // q becomes the start of the next segment
                acc = 0.0
            } else {
                acc += seg
            }
            i++
        }
        while (out.size < n) out.add(src.last())
        return if (out.size > n) out.subList(0, n).toList() else out
    }

    fun scaleUniform(p: List<Pt>, size: Double): List<Pt> {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (q in p) { minX = min(minX, q.x); minY = min(minY, q.y); maxX = max(maxX, q.x); maxY = max(maxY, q.y) }
        val s = max(maxX - minX, maxY - minY)
        if (s <= 0) return p
        val k = size / s
        return p.map { Pt(it.x * k, it.y * k) }
    }

    fun centroid(p: List<Pt>): Pt {
        var x = 0.0; var y = 0.0
        for (q in p) { x += q.x; y += q.y }
        return Pt(x / p.size, y / p.size)
    }

    fun translateToOrigin(p: List<Pt>): List<Pt> {
        val c = centroid(p)
        return p.map { Pt(it.x - c.x, it.y - c.y) }
    }

    fun rotateBy(p: List<Pt>, radians: Double): List<Pt> {
        val c = centroid(p)
        val cs = cos(radians); val sn = sin(radians)
        return p.map {
            val dx = it.x - c.x; val dy = it.y - c.y
            Pt(dx * cs - dy * sn + c.x, dx * sn + dy * cs + c.y)
        }
    }

    /**
     * Straight strokes carry almost no shape, only a direction, and $1's scaling can bend a
     * diagonal toward vertical. When a stroke is nearly straight it is matched by direction
     * against the straight-line templates instead. Returns null for curved strokes.
     */
    fun matchLine(raw: List<Pt>, templates: List<Template>): Match? {
        val lines = templates.filter { it.lineAngle != null }
        if (lines.isEmpty() || raw.size < 2) return null
        val len = pathLength(raw)
        val a = raw.first(); val b = raw.last()
        if (len <= 0 || dist(a, b) / len < 0.92) return null
        val angle = Math.toDegrees(atan2(b.y - a.y, b.x - a.x))
        return lines.map { t ->
            var d = kotlin.math.abs(angle - t.lineAngle!!) % 360.0
            if (d > 180) d = 360 - d
            Match(t, 1.0 - d / 90.0)
        }.maxBy { it.score }
    }

    private fun distanceAtBestAngle(p: List<Pt>, t: List<Pt>, from: Double, to: Double): Double {
        var a = from; var b = to
        var x1 = PHI * a + (1 - PHI) * b
        var f1 = distanceAtAngle(p, t, x1)
        var x2 = (1 - PHI) * a + PHI * b
        var f2 = distanceAtAngle(p, t, x2)
        while (b - a > PRECISION) {
            if (f1 < f2) {
                b = x2; x2 = x1; f2 = f1
                x1 = PHI * a + (1 - PHI) * b
                f1 = distanceAtAngle(p, t, x1)
            } else {
                a = x1; x1 = x2; f1 = f2
                x2 = (1 - PHI) * a + PHI * b
                f2 = distanceAtAngle(p, t, x2)
            }
        }
        return min(min(f1, f2), distanceAtAngle(p, t, 0.0))
    }

    private fun distanceAtAngle(p: List<Pt>, t: List<Pt>, radians: Double): Double =
        pathDistance(if (radians == 0.0) p else rotateBy(p, radians), t)

    private fun pathDistance(a: List<Pt>, b: List<Pt>): Double {
        val n = min(a.size, b.size)
        var d = 0.0
        for (i in 0 until n) d += dist(a[i], b[i])
        return d / n
    }

    private fun dist(a: Pt, b: Pt) = hypot(b.x - a.x, b.y - a.y)
}
