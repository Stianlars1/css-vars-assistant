package cssvarsassistant.completion

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class CssVariableCompletionHarnessTest : CssVarsAssistantPlatformTestCase() {

    fun testCompletionReturnsProjectCustomProperties() {
        addProjectStylesheet(
            "tokens.css",
            """
            :root {
              --accent-1: #111111;
              --accent-2: #222222;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              color: var(--acc<caret>);
            }
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertContainsElements(displayedNames, "accent-1", "accent-2")
    }

    fun testCssIndexCanBeQueriedFromTests() {
        addProjectStylesheet(
            "tokens.css",
            """
            :root {
              --space-sm: 8px;
            }
            """
        )

        val entries = readIndexedCssEntries("--space-sm")

        assertEquals(1, entries.size)
        assertEquals("default", entries.single().context)
        assertEquals("8px", entries.single().value)
    }
}
