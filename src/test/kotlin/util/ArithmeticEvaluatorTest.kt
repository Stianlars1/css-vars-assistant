package cssvarsassistant.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArithmeticEvaluatorTest {
    @Test
    fun `simple multiplication`() {
        assertEquals("48px", ArithmeticEvaluator.evaluate("8px * 6"))
    }

    @Test
    fun `addition with units`() {
        assertEquals("12px", ArithmeticEvaluator.evaluate("5px + 7px"))
    }

    @Test
    fun `unsupported mixed units`() {
        assertNull(ArithmeticEvaluator.evaluate("5px + 1em"))
    }

    @Test
    fun `hex color returns null`() {
        assertNull(ArithmeticEvaluator.evaluate("#001032"))
    }
}