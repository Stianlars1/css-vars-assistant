package cssvarsassistant.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreprocessorUtilTest {
    @Test
    fun `apply arithmetic with integers`() {
        assertEquals("16px", PreprocessorUtil.applyArithmetic("4px", 4.0))
    }

    @Test
    fun `apply arithmetic with decimals`() {
        assertEquals("-4.5rem", PreprocessorUtil.applyArithmetic("1.5rem", -3.0))
    }

    @Test
    fun `apply arithmetic with unsupported value`() {
        assertNull(PreprocessorUtil.applyArithmetic("blue", 2.0))
    }
}
