package cssvarsassistant.audit

import cssvarsassistant.documentation.ColorParser
import cssvarsassistant.util.ValueUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueEdgeCaseAuditTest {
    @Test fun `hsl alpha survives parsing`() {
        assertEquals(128, assertNotNull(ColorParser.parseCssColor("hsl(0 100% 50% / 50%)")).alpha)
    }
    @Test fun `legacy hsla alpha survives parsing`() {
        assertEquals(64, assertNotNull(ColorParser.parseCssColor("hsla(0, 100%, 50%, 0.25)")).alpha)
    }
    @Test fun `hue wraps beyond one full turn`() {
        assertEquals(ColorParser.parseCssColor("hsl(60 100% 50%)"), ColorParser.parseCssColor("hsl(420 100% 50%)"))
    }
    @Test fun `negative hue wraps`() {
        assertEquals(ColorParser.parseCssColor("hsl(300 100% 50%)"), ColorParser.parseCssColor("hsl(-60 100% 50%)"))
    }
    @Test fun `color functions are case insensitive`() {
        assertEquals(ColorParser.parseCssColor("rgb(255 0 0)"), ColorParser.parseCssColor("RGB(255 0 0)"))
    }
    @Test fun `fractional RGB channels are accepted`() {
        assertNotNull(ColorParser.parseCssColor("rgb(127.5 0 0)"))
    }
    @Test fun `malformed bare HSL returns null without throwing`() {
        assertNull(ColorParser.parseCssColor("1..2 50% 50%"))
    }
    @Test fun `overflow bare HSL hue returns null without throwing`() {
        assertNull(ColorParser.parseCssColor("9999999999999999999999999999999999999999 50% 50%"))
    }
    @Test fun `overflow bare HSL percentage returns null without throwing`() {
        assertNull(ColorParser.parseCssColor("0 9999999999999999999999999999999999999999% 50%"))
    }
    @Test fun `negative lengths are sizes`() {
        assertEquals(ValueUtil.ValueType.SIZE, ValueUtil.getValueType("-0.5rem"))
        assertEquals(-8.0, ValueUtil.convertToPixels("-0.5rem"))
    }
    @Test fun `leading decimal lengths are sizes`() {
        assertTrue(ValueUtil.isSizeValue(".5rem"))
        assertEquals(8.0, ValueUtil.convertToPixels(".5rem"))
    }
    @Test fun `valid hex alpha control`() {
        assertEquals("#7F80FF1A", ColorParser.colorToHex(assertNotNull(ColorParser.parseCssColor("#7F80FF1A"))))
    }
}
