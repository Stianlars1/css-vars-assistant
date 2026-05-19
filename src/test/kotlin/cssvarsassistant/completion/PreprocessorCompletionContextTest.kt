package cssvarsassistant.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreprocessorCompletionContextTest {

    @Test
    fun `extracts scss dollar usage query`() {
        val text = ".button { color: ${'$'}brand-pri }"
        val offset = text.indexOf("${'$'}brand-pri") + "${'$'}brand-pri".length

        val query = extractPreprocessorCompletionQuery("scss", text, offset)

        assertEquals(PreprocessorCompletionQuery('$', "\$brand-pri", "brand-pri"), query)
    }

    @Test
    fun `extracts less at usage query`() {
        val text = ".button { color: @brand-pri; }"
        val offset = text.indexOf("@brand-pri") + "@brand-pri".length

        val query = extractPreprocessorCompletionQuery("less", text, offset)

        assertEquals(PreprocessorCompletionQuery('@', "@brand-pri", "brand-pri"), query)
    }

    @Test
    fun `extracts blank scss value query right after dollar sign`() {
        val text = ".button { color: ${'$'} }"
        val offset = text.indexOf("${'$'} ") + 1

        val query = extractPreprocessorCompletionQuery("scss", text, offset)

        assertEquals(PreprocessorCompletionQuery('$', "\$", ""), query)
    }

    @Test
    fun `does not trigger in plain css files`() {
        val text = ".button { color: ${'$'}brand-pri; }"
        val offset = text.indexOf("${'$'}brand-pri") + "${'$'}brand-pri".length

        assertNull(extractPreprocessorCompletionQuery("css", text, offset))
    }

    @Test
    fun `does not trigger on scss declaration left hand side`() {
        val text = "${'$'}brand-primary: #7f80ff;"
        val offset = text.indexOf("${'$'}brand-primary") + "${'$'}brand-primary".length

        assertNull(extractPreprocessorCompletionQuery("scss", text, offset))
    }

    @Test
    fun `does not trigger on sass declaration left hand side`() {
        val text = "  ${'$'}brand-primary: #7f80ff"
        val offset = text.indexOf("${'$'}brand-primary") + "${'$'}brand-primary".length

        assertNull(extractPreprocessorCompletionQuery("sass", text, offset))
    }

    @Test
    fun `does not trigger on less declaration left hand side`() {
        val text = "@brand-primary: #7f80ff;"
        val offset = text.indexOf("@brand-primary") + "@brand-primary".length

        assertNull(extractPreprocessorCompletionQuery("less", text, offset))
    }

    @Test
    fun `does not trigger less at variables in scss files`() {
        val text = ".button { color: @brand-pri; }"
        val offset = text.indexOf("@brand-pri") + "@brand-pri".length

        assertNull(extractPreprocessorCompletionQuery("scss", text, offset))
    }
}
