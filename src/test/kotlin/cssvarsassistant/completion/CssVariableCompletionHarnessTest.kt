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

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              color: var(--fore<caret>);
            }
            """
        )

        // With multiple matching --foreground*/--*-foreground variables there is no
        // single-match auto-insert, so the lookup must stay open and we can assert
        // ranking directly. The earlier `if (lookups.isEmpty())` escape hatch
        // quietly accepted auto-insert as a pass and masked real regressions.
        val displayedNames = lookups.mapNotNull { it.itemText }
        assertEquals("foreground", displayedNames.first())
        assertEquals("foreground-subtle", displayedNames.drop(1).first())
        assertDoesntContain(displayedNames, "error-foreground")
        assertDoesntContain(displayedNames, "muted-foreground")
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

    fun testDoesNotOfferCssVariablesInTypeScriptFiles() {
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
            "app.ts",
            """
            const accent = "var(--acc<caret>)";
            """
        )

        val displayedNames = lookups.mapNotNull { it.itemText }
        assertDoesntContain(displayedNames, "accent-1")
        assertDoesntContain(displayedNames, "accent-2")
    }

    fun testDoesNotOfferCssVariablesWhenCompletingPropertyNames() {
        addProjectStylesheet(
            "background-tokens.css",
            """
            :root {
              --background: #111111;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .wrapper {
              backgro<caret>
            }
            """
        )

        val pluginSignaturePresent = lookups.any { lookup ->
            lookup.lookupString == "--background" &&
                lookup.itemText == "background" &&
                lookup.typeText == "#111111"
        }
        assertFalse(lookups.joinToString(), pluginSignaturePresent)
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

    // ---------------------------------------------------------------------
    // Insertion regression tests (1.7.1 → 1.7.2)
    //
    // `result.withPrefixMatcher("")` left the replacement range mis-aligned
    // with what the user had typed, producing `var(----name)` /
    // `var(---name)` / `var(--name-)` when a lookup element was inserted.
    // The fix keeps the prefix *length* correct (so IntelliJ replaces the
    // typed `--xxx`) while still permissively accepting every element we
    // add. These cases must also hold for `var()` nested inside any other
    // CSS function (hsl / rgb / calc / color-mix / linear-gradient / …).
    // ---------------------------------------------------------------------

    /**
     * Verifies that selecting the lookup element for [pickLookupString]
     * (e.g. `"--bg"`) rewrites the `var(...)` at the caret into the clean
     * expected form. When the typed prefix already narrows the list to a
     * single candidate, IntelliJ auto-inserts and we skip the lookup.
     */
    private fun assertCleanInsertion(
        beforeBody: String,
        expectedBody: String,
        pickLookupString: String
    ) {
        configureProjectFile(
            "app.css",
            """
            :root {
              --background: #111111;
              --bg: 0 0% 0%;
              --size: 16px;
            }

            .card {
              $beforeBody
            }
            """
        )
        val elements = myFixture.completeBasic()
        if (elements != null && elements.isNotEmpty()) {
            val target = elements.firstOrNull { it.lookupString == pickLookupString }
                ?: error(
                    "lookup did not contain '$pickLookupString'; available: " +
                        elements.joinToString { it.lookupString }
                )
            myFixture.lookup.currentItem = target
            myFixture.finishLookup('\n')
        }
        myFixture.checkResult(
            """
            :root {
              --background: #111111;
              --bg: 0 0% 0%;
              --size: 16px;
            }

            .card {
              $expectedBody
            }
            """.trimIndent()
        )
    }

    fun testInsertionWithPartialDashedPrefixProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: var(--back<caret>);",
            expectedBody = "color: var(--background);",
            pickLookupString = "--background"
        )
    }

    fun testInsertionRightAfterDashesProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: var(--<caret>);",
            expectedBody = "color: var(--background);",
            pickLookupString = "--background"
        )
    }

    fun testInsertionOnAlreadyCompleteNameDoesNotAddTrailingDash() {
        assertCleanInsertion(
            beforeBody = "color: var(--background<caret>);",
            expectedBody = "color: var(--background);",
            pickLookupString = "--background"
        )
    }

    fun testInsertionInsideHslFunctionProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "background: hsl(var(--b<caret>));",
            expectedBody = "background: hsl(var(--bg));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideHslaWithTrailingArgsProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: hsla(var(--b<caret>), 0.5);",
            expectedBody = "color: hsla(var(--bg), 0.5);",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideRgbFunctionProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: rgb(var(--b<caret>));",
            expectedBody = "color: rgb(var(--bg));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideRgbaFunctionProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: rgba(var(--b<caret>), 1);",
            expectedBody = "color: rgba(var(--bg), 1);",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideOklchFunctionProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "color: oklch(var(--b<caret>));",
            expectedBody = "color: oklch(var(--bg));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideColorMixProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "background: color-mix(in oklch, var(--b<caret>) 50%, white);",
            expectedBody = "background: color-mix(in oklch, var(--bg) 50%, white);",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInsideCalcProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "width: calc(var(--s<caret>) * 2);",
            expectedBody = "width: calc(var(--size) * 2);",
            pickLookupString = "--size"
        )
    }

    fun testInsertionInsideClampAsMiddleArgProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "width: clamp(0px, var(--s<caret>), 100px);",
            expectedBody = "width: clamp(0px, var(--size), 100px);",
            pickLookupString = "--size"
        )
    }

    fun testInsertionRewritesOnlyTheVarContainingTheCaret_Second() {
        assertCleanInsertion(
            beforeBody = "color: hsl(var(--background), var(--b<caret>));",
            expectedBody = "color: hsl(var(--background), var(--bg));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionRewritesOnlyTheVarContainingTheCaret_First() {
        assertCleanInsertion(
            beforeBody = "color: hsl(var(--b<caret>), var(--background));",
            expectedBody = "color: hsl(var(--bg), var(--background));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionInDoubleNestedFunctionsProducesCleanVar() {
        assertCleanInsertion(
            beforeBody = "background: linear-gradient(hsl(var(--background)), hsl(var(--b<caret>)));",
            expectedBody = "background: linear-gradient(hsl(var(--background)), hsl(var(--bg)));",
            pickLookupString = "--bg"
        )
    }

    fun testInsertionOutsideVarLeavesDocumentUnchanged() {
        // Caret is inside `hsl(...)` but NOT inside a `var(...)`. Our plugin
        // must not hijack the insertion here; the text must stay literally
        // the same (completion is effectively a no-op for us).
        configureProjectFile(
            "app.css",
            """
            :root {
              --background: #111111;
            }

            .card {
              background: hsl(100, 50%, 50<caret>);
            }
            """
        )
        val textBefore = myFixture.file.text
        myFixture.completeBasic()
        // Nothing starting with `--` was typed; our contributor must not
        // have rewritten `50` into a CSS variable. The document may have
        // IDE-level suggestions applied, but our plugin's signature output
        // (`var(--background)` etc.) must not appear at the caret location.
        assertFalse(
            "plugin must not rewrite non-var() contexts",
            myFixture.file.text.contains("var(--background)")
        )
        assertTrue(
            "document body unchanged near caret",
            myFixture.file.text.contains("hsl(100, 50%, 50")
        )
        // Silences unused warning in the rare case completeBasic() returns null.
        textBefore.hashCode()
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

    // ---------------------------------------------------------------------
    // Issue #18 — Bug A: a block comment above a custom property must not
    // leak its text into the completion popup's value slot. The popup must
    // still surface the *resolved* value (`#0000ff`), not description text
    // scraped from the comment.
    // ---------------------------------------------------------------------

    fun testDocCommentDoesNotLeakIntoValueColumn() {
        // Multiple `--primary*` variants so the popup stays open instead of
        // auto-inserting a single-variant match.
        configureProjectFile(
            "app.css",
            """
            :root {
              /**
               * @name Primary
               * @description Used for primary action buttons
               */
              --primary: #0000ff;
              --primary-hover: #0033ff;
              --primary-active: #0066ff;
            }

            .card {
              color: var(--prim<caret>);
            }
            """
        )

        val lookups = myFixture.completeBasic()
            ?.map {
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                it.renderElement(presentation)
                presentation
            }.orEmpty()

        val pluginEntry = lookups.firstOrNull { it.itemText == "primary" }
            ?: error("plugin did not surface --primary; items: " + lookups.joinToString { it.itemText ?: "<null>" })

        // The resolved value must appear somewhere in the lookup (typeText or
        // tailText). It must not be replaced by the description ("Used for
        // primary action").
        val rendered = "${pluginEntry.typeText.orEmpty()} ${pluginEntry.tailText.orEmpty()}"
        assertTrue(
            "expected resolved value in lookup presentation, got: $rendered",
            rendered.contains("#0000ff")
        )
        assertFalse(
            "description text must not replace the value slot: typeText='${pluginEntry.typeText}'",
            (pluginEntry.typeText ?: "").contains("Used for primary action")
        )
    }

    // ---------------------------------------------------------------------
    // Issue #18 — Bug A (local-override regression). A CSS file that
    // documents a past value inside a block comment used to make the popup
    // and hover show the commented-out value because the lastLocalValueInFile
    // regex did not strip comments. Locks in the fix: commented-out samples
    // are ignored, the real declaration wins.
    // ---------------------------------------------------------------------
    fun testLocalOverrideIgnoresCommentedOutDeclaration() {
        configureProjectFile(
            "app.css",
            """
            :root {
              --primary: #0000ff;
              --primary-hover: #0033ff;
              --primary-active: #0066ff;
            }

            /*
             * Changelog:
             *   --primary: purple; was used in v1 (deprecated).
             */

            .card {
              color: var(--prim<caret>);
            }
            """
        )

        val lookups = myFixture.completeBasic()
            ?.map {
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                it.renderElement(presentation)
                presentation
            }.orEmpty()

        val pluginEntry = lookups.firstOrNull { it.itemText == "primary" }
            ?: error("plugin did not surface --primary; items: " + lookups.joinToString { it.itemText ?: "<null>" })

        val rendered = "${pluginEntry.typeText.orEmpty()} ${pluginEntry.tailText.orEmpty()}"
        assertTrue(
            "expected real value '#0000ff' in lookup, got: $rendered",
            rendered.contains("#0000ff")
        )
        assertFalse(
            "commented-out 'purple' must not leak into lookup, got: $rendered",
            rendered.contains("purple")
        )
    }

    // ---------------------------------------------------------------------
    // Issue #18 — Bug B: the plugin must not offer CSS variable completions
    // when the caret is past the closing `)` of a `var(...)` on the same
    // line. The old isInsideVarFunction returned true for any caret position
    // that came after `var(` on the line, even when the matching `)` had
    // already been passed.
    // ---------------------------------------------------------------------
    fun testDoesNotFireAfterClosingParenOnSameLine() {
        addProjectStylesheet(
            "tokens.css",
            """
            :root {
              --primary: #0000ff;
              --secondary: #00ff00;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              color: var(--primary) <caret>;
            }
            """
        )

        // Our contributor must not have added any --* items: the caret is
        // outside any `var(...)`. Other IDE-level completions are allowed.
        val pluginItems = lookups.filter { it.lookupString.startsWith("--") }
        assertTrue(
            "plugin must not add CSS variable lookups outside var(); got: ${pluginItems.joinToString { it.lookupString }}",
            pluginItems.isEmpty()
        )
    }

    // As above but between two var() calls, where the cursor is between them
    // and therefore outside both parenthesised regions.
    fun testDoesNotFireBetweenTwoVarsOnSameLine() {
        addProjectStylesheet(
            "tokens.css",
            """
            :root {
              --a: #0000ff;
              --b: #00ff00;
            }
            """
        )

        val lookups = completeCssVariables(
            "app.css",
            """
            .card {
              background: hsl(var(--a)) <caret> var(--b);
            }
            """
        )

        val pluginItems = lookups.filter { it.lookupString.startsWith("--") }
        assertTrue(
            "plugin must not add CSS variable lookups between var() calls; got: ${pluginItems.joinToString { it.lookupString }}",
            pluginItems.isEmpty()
        )
    }
}
