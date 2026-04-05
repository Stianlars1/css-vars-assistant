package cssvarsassistant.completion

import cssvarsassistant.documentation.CssVariableDocumentationService
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

    fun testSemanticSizeFamiliesSortByResolvedValue() {
        addProjectStylesheet(
            "spacing-tokens-semantic-order.css",
            """
            :root {
              --spacing-2xs: 4px;
              --spacing-xs: 8px;
              --spacing-s: 12px;
              --spacing-m: 16px;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              gap: var(--spacing<caret>);
            }
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertEquals(listOf("spacing-2xs", "spacing-xs", "spacing-s", "spacing-m"), displayedNames.take(4))
    }

    fun testExactPrefixRanksAheadOfForegroundSuffixMatches() {
        addProjectStylesheet(
            "foreground-tokens.css",
            """
            :root {
              --foreground: #111111;
              --foreground-subtle: #666666;
              --error-foreground: #ff0000;
              --muted-foreground: #777777;
            }
            """
        )

        configureProjectFile(
            "app.css",
            """
            .card {
              color: var(--fore<caret>);
            }
            """
        )

        val lookups = myFixture.completeBasic()
            ?.map {
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                it.renderElement(presentation)
                presentation.itemText
            }
            .orEmpty()

        if (lookups.isEmpty()) {
            assertTrue(myFixture.file.text.contains("var(--foreground)"))
        } else {
            val displayedNames = lookups.filterNotNull()
            assertEquals("foreground", displayedNames.first())
            assertEquals("foreground-subtle", displayedNames.drop(1).first())
            assertDoesntContain(displayedNames, "error-foreground")
            assertDoesntContain(displayedNames, "muted-foreground")
        }
    }

    fun testForegroundColorQueryFallsBackToForegroundFamily() {
        addProjectStylesheet(
            "foreground-color-query.css",
            """
            :root {
              --foreground: #111111;
              --foreground-subtle: #666666;
              --error-foreground: #ff0000;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              color: hsl(var(--foreground-color<caret>));
            }
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertEquals(listOf("foreground", "foreground-subtle"), displayedNames.take(2))
        assertDoesntContain(displayedNames, "error-foreground")
    }

    fun testLocalOverrideWinsInCompletionAndDocumentation() {
        configureProjectFile(
            "app.css",
            """
            :root {
              --panel-gap: 8px;
              --panel-gap: 16px;
            }

            .card {
              gap: var(--panel<caret>);
            }
            """
        )

        val indexedValues = readIndexedCssEntries("--panel-gap").map { it.value }
        assertContainsElements(indexedValues, "8px", "16px")

        val variableElement = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset - 1))

        val hint = CssVariableDocumentationService.generateHint(variableElement, "--panel-gap")
        assertEquals("--panel-gap → 16px", hint)

        val html = CssVariableDocumentationService.generateDocumentation(variableElement, "--panel-gap")
        requireNotNull(html)
        assertTrue("expected local override to render before imported value", html.indexOf("16px") < html.indexOf("8px"))
    }

    fun testNestedRuleClosuresDoNotDropMediaContext() {
        addProjectStylesheet(
            "contexts.css",
            """
            @media (min-width: 768px) {
              .card {
                color: red;
              }

              :root {
                --layout-gap: 24px;
              }
            }
            """
        )

        val entries = readIndexedCssEntries("--layout-gap")

        assertContainsElements(entries.map { it.context }, "(min-width: 768px)")
    }

    fun testCompoundMediaQueryContextIsNormalized() {
        addProjectStylesheet(
            "compound-contexts.css",
            """
            @media screen and (min-width: 768px) and (prefers-color-scheme: dark) {
              :root {
                --surface-accent: #111111;
              }
            }
            """
        )

        val entries = readIndexedCssEntries("--surface-accent")

        assertContainsElements(
            entries.map { it.context },
            "screen and (min-width: 768px) and (prefers-color-scheme: dark)"
        )
    }
}
