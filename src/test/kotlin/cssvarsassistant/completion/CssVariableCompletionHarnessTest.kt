package cssvarsassistant.completion

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class CssVariableCompletionHarnessTest : CssVarsAssistantPlatformTestCase() {

    fun testCompletionReturnsProjectCustomProperties() {
        addProjectStylesheet(
            "accent-tokens.css",
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
            "space-index.css",
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

    fun testStrongPrefixMatchesHideWeakerSubstringMatches() {
        addProjectStylesheet(
            "spacing-tokens.css",
            """
            :root {
              --spacing-3xs: 2px;
              --spacing-2xs: 4px;
              --spacing-xs: 8px;
              --typography-body-medium-letter-spacing: 0.005em;
              --typography-body-large-letter-spacing: 0.01em;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              padding: var(--spacing<caret>);
            }
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertContainsElements(displayedNames, "spacing-3xs", "spacing-2xs", "spacing-xs")
        assertDoesntContain(displayedNames, "typography-body-medium-letter-spacing")
        assertDoesntContain(displayedNames, "typography-body-large-letter-spacing")
    }

    fun testMatchedNumericFamiliesSortNaturally() {
        addProjectStylesheet(
            "accent-tokens-natural-order.css",
            """
            :root {
              --accent-1: #0000ff;
              --accent-2: #ff0000;
              --accent-10: #00ff00;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              color: var(--accent<caret>);
            }
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertEquals(listOf("accent-1", "accent-2", "accent-10"), displayedNames.take(3))
    }
}
