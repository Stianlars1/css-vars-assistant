package cssvarsassistant.audit

import cssvarsassistant.documentation.CssVariableDocumentationService
import cssvarsassistant.documentation.HoverRow
import cssvarsassistant.documentation.ResolutionInfo
import cssvarsassistant.documentation.buildHtmlDocument
import cssvarsassistant.documentation.v2.CssVariablePsiDocumentationTargetProvider
import cssvarsassistant.model.CssVarDoc
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class EditorBehaviorAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testCssAtRuleIsNotClaimedAsLessVariableDocumentation() {
        val file = myFixture.configureByText("app.css", "@media (min-width: 800px) { body { color: red; } }")
        val element = requireNotNull(file.findElementAt(2))
        assertNull(CssVariablePsiDocumentationTargetProvider().documentationTarget(element, element))
    }

    fun testScssUseIsNotClaimedAsLessVariableDocumentation() {
        val file = myFixture.configureByText("app.scss", "@use 'sass:color';")
        val element = requireNotNull(file.findElementAt(2))
        assertNull(CssVariablePsiDocumentationTargetProvider().documentationTarget(element, element))
    }

    fun testLessVariableInCommentIsNotDocumentationTarget() {
        val file = myFixture.configureByText("app.less", "/* @brand */")
        val element = requireNotNull(file.findElementAt(5))
        assertNull(CssVariablePsiDocumentationTargetProvider().documentationTarget(element, element))
    }

    fun testCssCompletionDoesNotOfferTokensInsideComment() {
        addProjectStylesheet("tokens.css", ":root { --audit-brand: red; --audit-other: blue; }")
        val items = completeCssVariables("app.css", "/* example: var(--audit-<caret>) */")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("--audit-") })
    }

    fun testScssCompletionDoesNotOfferTokensInsideString() {
        addProjectStylesheet("tokens.scss", "${'$'}audit-brand: red;\n${'$'}audit-other: blue;")
        val items = completeCssVariables("app.scss", ".a { content: \"${'$'}audit-<caret>\"; }")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("${'$'}audit-") })
    }

    fun testMultilineScssPropertyValueOffersVariables() {
        addProjectStylesheet("tokens.scss", "${'$'}audit-brand: red;\n${'$'}audit-other: blue;")
        val items = completeCssVariables("app.scss", ".a {\n color:\n  ${'$'}audit-<caret>;\n}")
        assertContainsElements(items.map { it.lookupString }, "${'$'}audit-brand", "${'$'}audit-other")
    }

    fun testDirectPreprocessorThemeOverrideIsNotDiscardedByIndex() {
        addProjectStylesheet("tokens.less", "@night: black;\n:root { --bg: white; }\n.dark { --bg: @night; }")
        assertContainsElements(readIndexedCssEntries("--bg").map { it.context to it.value }, ".dark" to "@night")
    }

    fun testNamespacedScssCompletionUsesSelectedModule() {
        addProjectStylesheet("_first.scss", "${'$'}audit-brand: red;\n${'$'}audit-other: yellow;")
        addProjectStylesheet("_second.scss", "${'$'}audit-brand: blue;\n${'$'}audit-other: green;")
        val observed = linkedMapOf<String, String?>()
        for (module in listOf("first", "second")) {
            val items = completeCssVariablesInProjectFile("$module-app.scss", "@use './first';\n@use './second';\n.a { color: $module.${'$'}audit-<caret>; }")
            observed[module] = items.single { it.lookupString == "${'$'}audit-brand" }.typeText
            myFixture.lookup?.hideLookup(true)
        }
        assertEquals(mapOf("first" to "red", "second" to "blue"), observed)
    }

    fun testScssDocumentationRespectsDeclarationOrderAtUsage() {
        configureProjectFile("app.scss", "${'$'}brand: red;\n.a { color: ${'$'}brand<caret>; }\n${'$'}brand: blue;")
        val element = requireNotNull(myFixture.file.findElementAt(myFixture.editor.caretModel.offset - 1))
        val hint = requireNotNull(CssVariableDocumentationService.generateHint(element, "${'$'}brand"))
        assertTrue(hint, hint.endsWith("red"))
    }

    fun testImportedSourcePointsToDependencyFile() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        addProjectStylesheet("node_modules/vendor/tokens.css", ":root { --audit-brand: red; }")
        configureProjectFile("app.css", "@import 'vendor/tokens.css';\n.a { color: var(--audit-brand<caret>); }")
        val html = requireNotNull(CssVariableDocumentationService.generateDocumentation(myFixture.file, "--audit-brand"))
        assertTrue(html, html.contains("tokens.css:1"))
    }

    fun testDocumentationPreservesCaseSensitiveValue() {
        val value = "url(LogoDark.svg)"
        val html = buildHtmlDocument("--image", CssVarDoc(), listOf(HoverRow("default", ResolutionInfo(value, value), "", "tokens.css", 1)), false)
        assertTrue(html, html.contains("<nobr>url(LogoDark.svg)</nobr>"))
    }

    fun testDocumentationEscapesMarkupInValues() {
        val value = "<b>literal</b>"
        val html = buildHtmlDocument("--label", CssVarDoc(), listOf(HoverRow("default", ResolutionInfo(value, value), "", "tokens.css", 1)), false)
        assertTrue(html, html.contains("&lt;b&gt;literal&lt;/b&gt;"))
    }
}
