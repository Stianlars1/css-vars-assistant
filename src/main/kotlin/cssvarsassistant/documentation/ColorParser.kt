package cssvarsassistant.documentation

import com.intellij.ui.Gray
import java.awt.Color
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.roundToInt

object ColorParser {
    // ---- Regexes for various CSS syntaxes ----
    private val hexRe = Regex("^#([0-9a-fA-F]{3,8})$")
    private val rgbRe = Regex("^rgba?\\(([^)]*)\\)$")
    private val hslRe = Regex("^hsla?\\(([^)]*)\\)$")
    private val bareHslRe = Regex("^([\\d.]+)\\s+([\\d.]+%)\\s+([\\d.]+%)$")
    private val hwbRe = Regex("^hwb\\(([^)]*)\\)$")
    private val namedColors = mapOf(
        "red" to Color(255, 0, 0),
        "green" to Color(0, 128, 0),
        "blue" to Color(0, 0, 255),
        "white" to Gray._255,
        "black" to Gray._0,
        "yellow" to Color(255, 255, 0),
        "cyan" to Color(0, 255, 255),
        "magenta" to Color(255, 0, 255),
        "orange" to Color(255, 165, 0),
        "purple" to Color(128, 0, 128),
        "brown" to Color(165, 42, 42),
        "pink" to Color(255, 192, 203),
        "gray" to Gray._128,
        "grey" to Gray._128,
        "transparent" to Color(0, 0, 0, 0),
        "aliceblue" to Color(240, 248, 255),
        "aqua" to Color(0, 255, 255),
        "coral" to Color(255, 127, 80),
        "crimson" to Color(220, 20, 60),
        "deepskyblue" to Color(0, 191, 255),
        "dodgerblue" to Color(30, 144, 255),
        "gold" to Color(255, 215, 0),
        "lightgray" to Color(211, 211, 211),
        "lightgrey" to Color(211, 211, 211),
        "lime" to Color(0, 255, 0),
        "maroon" to Color(128, 0, 0),
        "navy" to Color(0, 0, 128),
        "olive" to Color(128, 128, 0),
        "rebeccapurple" to Color(102, 51, 153),
        "salmon" to Color(250, 128, 114),
        "silver" to Color(192, 192, 192),
        "slategray" to Color(112, 128, 144),
        "slategrey" to Color(112, 128, 144),
        "teal" to Color(0, 128, 128)
    )

    /**
     * Parses a CSS color string to a java.awt.Color, or null if not recognized.
     * Supports hex, rgb(a), hsl(a), shadcn “0 0% 100%” (bare HSL), HWB, etc.
     */
    fun parseCssColor(raw: String): Color? {
        val s = raw.trim()
        val cleaned = raw.trim().lowercase()
        namedColors[cleaned]?.let { return it }

        hexRe.matchEntire(s)?.groupValues?.get(1)?.let { return parseHexColor(it) }
        rgbRe.matchEntire(s)?.groupValues?.get(1)?.let { return parseRgbColor(it) }
        hslRe.matchEntire(s)?.groupValues?.get(1)?.let { return parseHslColor(it) }
        bareHslRe.matchEntire(s)?.destructured?.let { (h, s2, l2) ->
            return hslToColor(h.toFloat(), s2.removeSuffix("%").toFloat(), l2.removeSuffix("%").toFloat())
        }
        hwbRe.matchEntire(s)?.groupValues?.get(1)?.let { return parseHwbColor(it) }

        return null
    }

    // Issue #22 — CSS Color Level 4 hex syntaxes:
    //   3-digit  #RGB      → #RRGGBB
    //   4-digit  #RGBA     → #RRGGBBAA   (alpha LAST)
    //   6-digit  #RRGGBB
    //   8-digit  #RRGGBBAA              (alpha LAST)
    //
    // Earlier 8-digit handling treated the first byte as alpha (Java `Color.getRGB()`
    // ARGB packed-int order), which produced wrong RGB plus a dropped alpha — the
    // visible symptom in #22 was `#7F80FF1A` rendering as `#80FF1A` in the Hex
    // column. The CSS spec orders alpha last, and we now follow it. 4-digit
    // shorthand is also accepted; it was previously rejected because the
    // `length == 8` branch was the only alpha-aware path.
    private fun parseHexColor(hex: String): Color? = try {
        // Each digit doubled (`a` → `aa`) gives the canonical 6/8-digit form.
        // We delegate to one branch with explicit RGBA byte slots, which keeps
        // the parser DRY and removes the off-by-one trap in the original
        // 8-digit branch.
        val expanded = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("") + "ff"            // #RGB    → #RRGGBBFF
            4 -> hex.map { "$it$it" }.joinToString("")                    // #RGBA   → #RRGGBBAA
            6 -> "${hex}ff"                                                // #RRGGBB → #RRGGBBFF
            8 -> hex                                                       // #RRGGBBAA verbatim
            else -> return null
        }

        Color(
            Integer.valueOf(expanded.substring(0, 2), 16), // R
            Integer.valueOf(expanded.substring(2, 4), 16), // G
            Integer.valueOf(expanded.substring(4, 6), 16), // B
            Integer.valueOf(expanded.substring(6, 8), 16)  // A (CSS spec: last byte)
        )
    } catch (_: Exception) {
        null
    }

    private fun parseRgbColor(rgb: String): Color? {
        val cleaned = rgb.replace("/", " ")
        val parts = cleaned.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        return try {
            val (r, g, b) = parts.take(3).mapIndexed { i, v ->
                when {
                    v.endsWith("%") -> (255 * v.removeSuffix("%").toFloat() / 100).roundToInt()
                    else -> v.toInt()
                }.coerceIn(0, 255)
            }
            // Alpha, if present
            val a = parts.getOrNull(3)?.let {
                when {
                    it.endsWith("%") -> (255 * it.removeSuffix("%").toFloat() / 100).roundToInt().coerceIn(0, 255)
                    it.toFloatOrNull() != null -> (it.toFloat() * 255).roundToInt().coerceIn(0, 255)
                    else -> 255
                }
            } ?: 255
            Color(r, g, b, a)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHslColor(hsl: String): Color? {
        val cleaned = hsl.replace("/", " ").replace(",", " ")
        val parts = cleaned.split(' ').filter { it.isNotBlank() }
        return try {
            val h = parseHue(parts[0]) ?: return null
            val s = parts[1].removeSuffix("%").toFloat()
            val l = parts[2].removeSuffix("%").toFloat()
            hslToColor(h, s, l)
        } catch (_: Exception) {
            null
        }
    }

    /** Converts HSL to Color. Accepts s/l as percent (0–100). */
    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val s1 = s / 100f
        val l1 = l / 100f
        val c = (1 - abs(2 * l1 - 1)) * s1
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = l1 - c / 2
        val (r1, g1, b1) = when {
            h < 60 -> listOf(c, x, 0f)
            h < 120 -> listOf(x, c, 0f)
            h < 180 -> listOf(0f, c, x)
            h < 240 -> listOf(0f, x, c)
            h < 300 -> listOf(x, 0f, c)
            else -> listOf(c, 0f, x)
        }
        return Color(
            ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255).roundToInt().coerceIn(0, 255)
        )
    }

    private fun parseHwbColor(hwb: String): Color? {
        val cleaned = hwb.replace("/", " ")
        val parts = cleaned.split(',', ' ').filter { it.isNotBlank() }
        if (parts.size < 3) return null
        return try {
            val h = parts[0].toFloat().rem(360f)
            val w = parts[1].removeSuffix("%").toFloat() / 100f
            val b = parts[2].removeSuffix("%").toFloat() / 100f
            val alpha = parts.getOrNull(3)?.let {
                if (it.endsWith("%")) it.removeSuffix("%").toFloat() / 100f
                else it.toFloatOrNull() ?: 1f
            } ?: 1f

            // Correct handling of edge cases (CSS spec):
            if ((w + b) >= 1f) {
                val grayness = (w / (w + b)).coerceIn(0f, 1f)
                val gray = (grayness * 255).roundToInt().coerceIn(0, 255)
                return Color(gray, gray, gray, (alpha * 255).roundToInt().coerceIn(0, 255))
            }

            val c = 1f - w - b
            val base = hslToColor(h, 100f, 50f)
            val r = (w + c * (base.red / 255f)).coerceIn(0f, 1f)
            val g = (w + c * (base.green / 255f)).coerceIn(0f, 1f)
            val bl = (w + c * (base.blue / 255f)).coerceIn(0f, 1f)
            Color(
                (r * 255).roundToInt(),
                (g * 255).roundToInt(),
                (bl * 255).roundToInt(),
                (alpha * 255).roundToInt()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHue(value: String): Float? {
        val cleaned = value.trim().lowercase()
        return when {
            cleaned.endsWith("deg") -> cleaned.removeSuffix("deg").toFloatOrNull()
            cleaned.endsWith("turn") -> cleaned.removeSuffix("turn").toFloatOrNull()?.times(360f)
            cleaned.endsWith("grad") -> cleaned.removeSuffix("grad").toFloatOrNull()?.times(0.9f)
            cleaned.endsWith("rad") -> cleaned.removeSuffix("rad").toFloatOrNull()?.times((180f / PI).toFloat())
            else -> cleaned.toFloatOrNull()
        }
    }

    /**
     * Canonical hex serialisation for a parsed [Color].
     *
     * Returns "#RRGGBB" for fully-opaque colors and "#RRGGBBAA" when the alpha
     * channel is below 255 — matching the input grammar accepted by
     * [parseCssColor]. Issue #22 fixed two compounding bugs here: the 8-digit
     * input parser used Java's ARGB byte order (alpha first) instead of the
     * CSS spec's RGBA order (alpha last), and this method silently dropped
     * alpha on the way out, so `#7F80FF1A` round-tripped as `#80FF1A`.
     */
    fun colorToHex(color: Color): String =
        if (color.alpha == 255) {
            String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        } else {
            String.format("#%02X%02X%02X%02X", color.red, color.green, color.blue, color.alpha)
        }

    /**
     * RGB-only hex without alpha — for callers like the WebAIM contrast link
     * whose URL grammar only understands 6-digit hex.
     */
    fun colorToRgbHex(color: Color): String =
        String.format("#%02X%02X%02X", color.red, color.green, color.blue)

    /**
     * Combines parsing + hex conversion in one call.
     * Returns e.g. "#1A90FF" or "#7F80FF1A" when alpha is present, or null if
     * it wasn't a valid color.
     */
    fun toHexString(input: String): String? {
        return parseCssColor(input)?.let(::colorToHex)
    }
}
