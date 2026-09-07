package cssvarsassistant.completion

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class CssVarCompletionBoundaryTest : CssVarsAssistantPlatformTestCase() {
    override fun setUp() {
        super.setUp()
        addProjectStylesheet("tokens.css", ":root { --boundary-red: red; --boundary-rose: pink; }")
    }

    fun testFallbackValuesInEveryStylesheetDialectAreLeftAlone() {
        for (extension in listOf("css", "scss", "less", "sass")) {
            assertCompletion(extension, "var(--missing, --boundary-r<caret>)", false)
            assertCompletion(extension, "var(--missing, rgb(--boundary-r<caret>))", false)
        }
    }

    fun testFirstArgumentsAndNestedFallbackVariablesRemainAvailable() {
        for (extension in listOf("css", "scss", "less", "sass")) {
            assertCompletion(extension, "var(--boundary-r<caret>, red)", true)
            assertCompletion(extension, "var(--missing, var(--boundary-r<caret>, red))", true)
            assertCompletion(extension, "rgb(var(--boundary-r<caret>))", true)
        }
    }

    fun testIncompleteCallsStillRespectTheArgumentBoundary() {
        assertCompletion("css", "var(--boundary-r<caret>", true)
        assertCompletion("css", "var(--missing, --boundary-r<caret>", false)
        assertCompletion("css", "var(--missing, var(--boundary-r<caret>", true)
        assertCompletion("css", "var(--missing, var(--other), --boundary-r<caret>", false)
    }

    fun testCommentsDoNotCreateArgumentSeparators() {
        assertCompletion("css", "var(/* fallback: , ) */ --boundary-r<caret>)", true)
        assertCompletion("css", "var(--missing, /* first: ( */ --boundary-r<caret>)", false)
    }

    fun testMultilineFirstArgumentRemainsAvailable() {
        assertCompletion("css", "var(\n  --boundary-r<caret>\n, red)", true)
        assertCompletion("css", "var(--missing,\n  --boundary-r<caret>)", false)
    }

    fun testDisablingIdeFallbackDoesNotClaimNonVariableArguments() {
        updateSettings { allowIdeCompletions = false }
        assertCompletion("css", "var(--missing, --boundary-r<caret>)", false)
        assertCompletion("css", "var(--boundary-r<caret>)", true)
    }

    fun testSassVariablesAreStillCompletedInsideCssFallbackValues() {
        val items = completeCssVariablesInProjectFile("app.scss", "\$brand-red: red;\n\$brand-rose: pink;\n.a { color: var(--missing, \$brand-r<caret>); }")
        assertContainsElements(items.map { it.lookupString }, "\$brand-red", "\$brand-rose")
    }

    private fun assertCompletion(extension: String, value: String, expected: Boolean) {
        val source = if (extension == "sass") ".a\n  color: $value" else ".a { color: $value; }"
        val items = completeCssVariables("app.$extension", source)
        val names = items.map { it.lookupString }.filter { it.startsWith("--boundary-") }
        myFixture.lookup?.hideLookup(true)
        if (expected) assertContainsElements(names, "--boundary-red", "--boundary-rose")
        else assertTrue("$extension $value: $names", names.isEmpty())
    }
}
