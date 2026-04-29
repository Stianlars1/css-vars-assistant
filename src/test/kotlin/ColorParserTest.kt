package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ColorParserTest {

    // ----- Hex -----

    @Test fun `hex expands and parses`() {
        val c = ColorParser.parseCssColor("#1e90ff")
        assertEquals("#1E90FF", c?.let(ColorParser::colorToHex))
    }

    @Test fun `shorthand hex expands`() {
        // #ABC → #AABBCC
        assertEquals("#AABBCC", ColorParser.toHexString("#ABC"))
    }

    // Issue #22 — 8-digit hex (#RRGGBBAA per CSS Color Level 4) was previously
    // mis-parsed as #AARRGGBB (Java ARGB int order) and the alpha was then
    // silently dropped. Both bugs combined to truncate `#7F80FF1A` to
    // `#80FF1A`. Round-trip through parseCssColor + colorToHex must now
    // preserve alpha verbatim.
    @Test fun `8-digit hex preserves alpha and uses CSS spec ordering`() {
        // Reported value from the issue: alpha is the LAST byte (1A), not the first.
        assertEquals("#7F80FF1A", ColorParser.toHexString("#7F80FF1A"))
    }

    @Test fun `8-digit hex round-trips when alpha is below full opacity`() {
        // #80FF0000 means R=0x80, G=0xFF, B=0x00, A=0x00 (fully transparent
        // yellow-green). The previous test expected `#FF0000` here because the
        // parser put alpha first AND the formatter dropped alpha. Both have
        // been corrected and now the round-trip is identity-preserving.
        assertEquals("#80FF0000", ColorParser.toHexString("#80FF0000"))
    }

    @Test fun `8-digit hex with full opacity collapses to 6 digits`() {
        // Alpha = FF means fully opaque; output stays 6-digit so existing
        // callers and snapshots aren't disturbed when no alpha is present.
        assertEquals("#1E90FF", ColorParser.toHexString("#1E90FFFF"))
    }

    @Test fun `4-digit hex shorthand expands with alpha`() {
        // #RGBA → #RRGGBBAA (each digit doubled, just like 3-digit shorthand).
        // Was previously unsupported (parser branched on 3/6/8 only).
        assertEquals("#AABBCCDD", ColorParser.toHexString("#ABCD"))
    }

    @Test fun `4-digit hex shorthand with full opacity collapses to 6 digits`() {
        assertEquals("#AABBCC", ColorParser.toHexString("#ABCF"))
    }

    @Test fun `parses 8-digit hex into Color object with correct channels`() {
        val c = ColorParser.parseCssColor("#7F80FF1A")
        assertNotNull(c)
        assertEquals(0x7F, c!!.red,   "red byte must come from positions 1-2")
        assertEquals(0x80, c.green, "green byte must come from positions 3-4")
        assertEquals(0xFF, c.blue,  "blue byte must come from positions 5-6")
        assertEquals(0x1A, c.alpha, "alpha byte must come from positions 7-8 (CSS spec)")
    }

    @Test fun `hex with surrounding whitespace`() {
        assertEquals("#AABBCC", ColorParser.toHexString("  #AaBbCc  "))
    }

    // ----- RGB / RGBA -----

    @Test fun `rgb integer syntax`() {
        assertEquals("#FF0080", ColorParser.toHexString("rgb(255, 0, 128)"))
    }

    @Test fun `rgb percentage syntax`() {
        // 100% → 255, 50% → 128
        assertEquals("#FF0080", ColorParser.toHexString("rgb(100%,0%,50%)"))
    }

    @Test fun `rgba slash alpha syntax preserves alpha`() {
        // alpha 50% → 128 (= 0x80). Issue #22 — alpha is now preserved in the
        // canonical hex output instead of being silently dropped.
        assertEquals("#FF008080", ColorParser.toHexString("rgba(255 0 128 / 50%)"))
    }

    @Test fun `rgba comma alpha syntax preserves alpha`() {
        // 0.5 → 128 (= 0x80). Issue #22 — alpha preserved.
        assertEquals("#00FF0080", ColorParser.toHexString("rgba(0, 255, 0, 0.5)"))
    }

    @Test fun `rgba with explicit full opacity stays 6 digits`() {
        // Fully opaque rgba should still emit 6-digit hex so existing snapshots
        // and downstream consumers (WebAIM contrast link, swatch background)
        // are not disturbed for the common opaque case.
        assertEquals("#FF0080", ColorParser.toHexString("rgba(255, 0, 128, 1)"))
    }

    // ----- HSL / HSLA -----

    @Test fun `hsl comma syntax`() {
        // pure green
        assertEquals("#00FF00", ColorParser.toHexString("hsl(120, 100%, 50%)"))
    }

    @Test fun `hsl space slash syntax`() {
        // pure blue, with 25% alpha ignored
        assertEquals("#0000FF", ColorParser.toHexString("hsl(240 100% 50% /25%)"))
    }

    @Test fun `hsl rem extended hue`() {
        // 360° ≡ 0°, pure red
        assertEquals("#FF0000", ColorParser.toHexString("hsl(360,100%,50%)"))
    }

    // ----- bare HSL triplet -----

    @Test fun `bare HSL triplet`() {
        assertEquals("#0000FF", ColorParser.toHexString("240 100% 50%"))
    }

    // ----- HWB -----

    @Test fun `hwb black and white zero`() {
        // hwb(0,0%,0%) should yield pure red (h=0 → red)
        assertEquals("#FF0000", ColorParser.toHexString("hwb(0,0%,0%)"))
    }

    @Test fun `hwb with commas and slash`() {
        // yellow-ish: h=60°, w=10%, b=10%
        val hex = ColorParser.toHexString("hwb(60, 10%, 10%)")
        // approximate expected result: mixing red&green with a little white/black
        assertEquals("#E6E61A", hex)
    }

    // ----- Invalid or unsupported -----

    @Test fun `invalid color yields null`() {
        assertNull(ColorParser.parseCssColor("notacolor"))
    }

    @Test fun `empty string yields null`() {
        assertNull(ColorParser.parseCssColor(""))
    }


    @Test fun `rgb decimal`() {
        assertEquals("#FF0000", ColorParser.toHexString("rgb(255,0,0)"))
    }
    @Test fun `rgb percent`() {
        assertEquals("#00FF00", ColorParser.toHexString("rgb(0%,100%,0%)"))
    }
    @Test fun `hsl with commas and slash`() {
        assertEquals("#0000FF", ColorParser.toHexString("hsl(240,100%,50%)"))
        assertEquals("#0000FF", ColorParser.toHexString("hsla(240 100% 50% / 1)"))
    }
    @Test fun `hwb works`() {
        assertEquals("#FF0000", ColorParser.toHexString("hwb(0 0% 0%)"))
    }

    @Test fun `named colors cover common css palette values`() {
        assertEquals("#663399", ColorParser.toHexString("rebeccapurple"))
        assertEquals("#F0F8FF", ColorParser.toHexString("aliceblue"))
        assertEquals("#1E90FF", ColorParser.toHexString("dodgerblue"))
    }

    @Test fun `hsl hue units are supported`() {
        assertEquals("#FF0000", ColorParser.toHexString("hsl(1turn 100% 50%)"))
        assertEquals("#00FF00", ColorParser.toHexString("hsl(133.333grad 100% 50%)"))
    }
}
