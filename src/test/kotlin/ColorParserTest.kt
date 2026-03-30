package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test fun `8‑digit hex drops alpha`() {
        // #80FF0000 has 50% alpha + red ; toHexString should drop alpha and yield red
        assertEquals("#FF0000", ColorParser.toHexString("#80FF0000"))
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

    @Test fun `rgba slash alpha syntax`() {
        // alpha 50% → 128 but dropped by toHexString()
        assertEquals("#FF0080", ColorParser.toHexString("rgba(255 0 128 / 50%)"))
    }

    @Test fun `rgba comma alpha syntax`() {
        assertEquals("#00FF00", ColorParser.toHexString("rgba(0, 255, 0, 0.5)"))
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
