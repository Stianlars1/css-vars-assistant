package cssvarsassistant.util

import cssvarsassistant.completion.CssVariableCompletion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValueUtilTest {

    // ----- Size Value Detection -----

    @Test
    fun `detects px values`() {
        assertTrue(ValueUtil.isSizeValue("16px"))
        assertTrue(ValueUtil.isSizeValue("1024px"))
        assertTrue(ValueUtil.isSizeValue("0px"))
        assertTrue(ValueUtil.isSizeValue("4.5px"))
    }

    @Test
    fun `detects rem values`() {
        assertTrue(ValueUtil.isSizeValue("1rem"))
        assertTrue(ValueUtil.isSizeValue("1.5rem"))
        assertTrue(ValueUtil.isSizeValue("0.25rem"))
    }

    @Test
    fun `detects em values`() {
        assertTrue(ValueUtil.isSizeValue("2em"))
        assertTrue(ValueUtil.isSizeValue("1.2em"))
    }

    @Test
    fun `detects percentage values`() {
        assertTrue(ValueUtil.isSizeValue("100%"))
        assertTrue(ValueUtil.isSizeValue("50.5%"))
        assertTrue(ValueUtil.isSizeValue("0%"))
    }

    @Test
    fun `detects viewport units`() {
        assertTrue(ValueUtil.isSizeValue("100vh"))
        assertTrue(ValueUtil.isSizeValue("50vw"))
        assertTrue(ValueUtil.isSizeValue("25.5vh"))
    }

    @Test
    fun `detects modern viewport and physical units`() {
        assertTrue(ValueUtil.isSizeValue("100dvh"))
        assertTrue(ValueUtil.isSizeValue("50svw"))
        assertTrue(ValueUtil.isSizeValue("12vmin"))
        assertTrue(ValueUtil.isSizeValue("3ch"))
        assertTrue(ValueUtil.isSizeValue("2cm"))
        assertTrue(ValueUtil.isSizeValue("1in"))
    }

    @Test
    fun `detects pt values`() {
        assertTrue(ValueUtil.isSizeValue("12pt"))
        assertTrue(ValueUtil.isSizeValue("14.5pt"))
    }

    @Test
    fun `ignores whitespace`() {
        assertTrue(ValueUtil.isSizeValue("  16px  "))
        assertTrue(ValueUtil.isSizeValue("\n1.5rem\t"))
    }

    @Test
    fun `case insensitive units`() {
        assertTrue(ValueUtil.isSizeValue("16PX"))
        assertTrue(ValueUtil.isSizeValue("1.5REM"))
        assertTrue(ValueUtil.isSizeValue("2EM"))
    }

    @Test
    fun `rejects invalid size values`() {
        assertFalse(ValueUtil.isSizeValue("16"))
        assertFalse(ValueUtil.isSizeValue("px"))
        assertFalse(ValueUtil.isSizeValue("16px extra"))
        assertFalse(ValueUtil.isSizeValue("red"))
        assertFalse(ValueUtil.isSizeValue("16px (+3)")) // This is key!
    }

    // ----- Value Type Detection -----

    @Test
    fun `classifies size values correctly`() {
        assertEquals(ValueUtil.ValueType.SIZE, ValueUtil.getValueType("16px"))
        assertEquals(ValueUtil.ValueType.SIZE, ValueUtil.getValueType("1024px"))
        assertEquals(ValueUtil.ValueType.SIZE, ValueUtil.getValueType("1.5rem"))
        assertEquals(ValueUtil.ValueType.SIZE, ValueUtil.getValueType("100%"))
    }

    @Test
    fun `classifies color values correctly`() {
        assertEquals(ValueUtil.ValueType.COLOR, ValueUtil.getValueType("#ff0000"))
        assertEquals(ValueUtil.ValueType.COLOR, ValueUtil.getValueType("rgb(255,0,0)"))
        assertEquals(ValueUtil.ValueType.COLOR, ValueUtil.getValueType("hsl(0,100%,50%)"))
        assertEquals(ValueUtil.ValueType.COLOR, ValueUtil.getValueType("0 100% 50%"))
    }

    @Test
    fun `classifies number values correctly`() {
        assertEquals(ValueUtil.ValueType.NUMBER, ValueUtil.getValueType("42"))
        assertEquals(ValueUtil.ValueType.NUMBER, ValueUtil.getValueType("3.14"))
        assertEquals(ValueUtil.ValueType.NUMBER, ValueUtil.getValueType("0"))
    }

    @Test
    fun `classifies other values correctly`() {
        assertEquals(ValueUtil.ValueType.OTHER, ValueUtil.getValueType("none"))
        assertEquals(ValueUtil.ValueType.OTHER, ValueUtil.getValueType("auto"))
        assertEquals(ValueUtil.ValueType.OTHER, ValueUtil.getValueType("16px (+3)"))
    }

    // ----- Pixel Conversion -----

    @Test
    fun `converts px correctly`() {
        assertEquals(16.0, ValueUtil.convertToPixels("16px"))
        assertEquals(1024.0, ValueUtil.convertToPixels("1024px"))
        assertEquals(4.5, ValueUtil.convertToPixels("4.5px"))
    }

    @Test
    fun `converts rem correctly`() {
        assertEquals(16.0, ValueUtil.convertToPixels("1rem"))
        assertEquals(24.0, ValueUtil.convertToPixels("1.5rem"))
        assertEquals(4.0, ValueUtil.convertToPixels("0.25rem"))
    }

    @Test
    fun `converts em correctly`() {
        assertEquals(32.0, ValueUtil.convertToPixels("2em"))
        assertEquals(19.2, ValueUtil.convertToPixels("1.2em"))
    }

    @Test
    fun `converts pt correctly`() {
        assertEquals(15.96, ValueUtil.convertToPixels("12pt"), 0.01)
    }

    @Test
    fun `converts additional css units consistently`() {
        assertEquals(16.0, ValueUtil.convertToPixels("1pc"))
        assertEquals(24.0, ValueUtil.convertToPixels("3ch"))
        assertEquals(75.6, ValueUtil.convertToPixels("2cm"), 0.01)
        assertEquals(96.0, ValueUtil.convertToPixels("1in"))
    }

    // ----- Size Comparison -----

    @Test
    fun `compares sizes correctly ascending`() {
        assertTrue(ValueUtil.compareSizes("16px", "24px") < 0)
        assertTrue(ValueUtil.compareSizes("1rem", "1.5rem") < 0)
        assertTrue(ValueUtil.compareSizes("24px", "16px") > 0)
        assertEquals(0, ValueUtil.compareSizes("16px", "1rem")) // Both = 16px
    }

    @Test
    fun `sorts pixel values correctly`() {
        val values = listOf("1440px", "1024px", "1280px", "16px")
        val sorted = values.sortedWith { a, b -> ValueUtil.compareSizes(a, b) }
        assertEquals(listOf("16px", "1024px", "1280px", "1440px"), sorted)
    }

    @Test
    fun `sorts mixed units correctly`() {
        val values = listOf("2rem", "16px", "1.5rem", "20px")
        val sorted = values.sortedWith { a, b -> ValueUtil.compareSizes(a, b) }
        assertEquals(listOf("16px", "20px", "1.5rem", "2rem"), sorted)
    }

    // ----- Number Comparison -----

    @Test
    fun `compares numbers correctly`() {
        assertTrue(ValueUtil.compareNumbers("1", "2") < 0)
        assertTrue(ValueUtil.compareNumbers("3.14", "2.5") > 0)
        assertEquals(0, ValueUtil.compareNumbers("42", "42"))
    }

    // ----- Integration Tests -----

    @Test
    fun `width variables should sort as SIZE type`() {
        val testValues = mapOf(
            "width-l" to "1024px",
            "width-2xl" to "1440px",
            "width-xl" to "1280px"
        )

        testValues.forEach { (name, value) ->
            assertEquals(
                ValueUtil.ValueType.SIZE, ValueUtil.getValueType(value),
                "Variable $name with value '$value' should be SIZE type"
            )
        }

        val sortedByValue = testValues.toList()
            .sortedWith { a, b -> ValueUtil.compareSizes(a.second, b.second) }

        assertEquals(
            listOf(
                "width-l" to "1024px",
                "width-xl" to "1280px",
                "width-2xl" to "1440px"
            ), sortedByValue
        )
    }


    @Test
    fun `sorts padding variables correctly ascending`() {
        val paddingEntries = listOf(
            CssVariableCompletion.Entry("--padding-2xl", "padding-2xl", "48px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-2xs", "padding-2xs", "4px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-lg", "padding-lg", "32px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-md", "padding-md", "24px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-sm", "padding-sm", "16px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-xl", "padding-xl", "40px", emptyList(), "", false, false),
            CssVariableCompletion.Entry("--padding-xs", "padding-xs", "8px", emptyList(), "", false, false)
        )

        val comparator = Comparator<CssVariableCompletion.Entry> { a, b ->
            val aType = ValueUtil.getValueType(a.mainValue)
            val bType = ValueUtil.getValueType(b.mainValue)

            if (aType != bType) {
                return@Comparator aType.ordinal - bType.ordinal
            }

            when (aType) {
                ValueUtil.ValueType.SIZE -> ValueUtil.compareSizes(a.mainValue, b.mainValue)
                else -> a.display.compareTo(b.display, true)
            }
        }

        val sorted = paddingEntries.sortedWith(comparator)

        val expected = listOf("4px", "8px", "16px", "24px", "32px", "40px", "48px")
        val actual = sorted.map { it.mainValue }

        assertEquals(expected, actual)
    }
}
