package cssvarsassistant.completion

import kotlin.test.Test
import kotlin.test.assertEquals

class CssVarCascadeUtilTest {

    @Test
    fun `selects last local declaration over indexed defaults`() {
        val activeFileText = """
            :root {
              --panel-gap: 8px;
              --panel-gap: 16px;
            }
        """.trimIndent()

        val resolvedValues = listOf(
            "default" to "8px",
            "default" to "16px"
        )

        val mainValue = CssVarCascadeUtil.selectMainValue("--panel-gap", activeFileText, resolvedValues)

        assertEquals("16px", mainValue)
    }

    @Test
    fun `falls back to last default when no local declaration exists`() {
        val resolvedValues = listOf(
            "default" to "4px",
            "default" to "8px"
        )

        val mainValue = CssVarCascadeUtil.selectMainValue("--space-sm", ".card { gap: var(--space-sm); }", resolvedValues)

        assertEquals("8px", mainValue)
    }
}
