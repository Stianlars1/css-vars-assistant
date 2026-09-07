package cssvarsassistant.audit

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.DumbModeTestUtils
import cssvarsassistant.documentation.CssVariableDocumentationService
import cssvarsassistant.documentation.v2.CssVariableDocumentationTarget
import cssvarsassistant.documentation.v2.CssVariablePsiDocumentationTargetProvider
import cssvarsassistant.index.ImportResolver
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class IdeInterferenceAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testNativeCssPropertyDocumentationRemainsAvailable() {
        assertNativeDocumentation("app.css", ".a { co<caret>lor: var(--brand); }")
    }

    fun testNativeCssColorDocumentationInCustomPropertyRemainsAvailable() {
        assertNativeDocumentation("app.css", ":root { --brand: re<caret>d; }")
    }

    fun testNativeColorDocumentationControlWithoutPluginProvider() {
        val point = ExtensionPointName.create<PsiDocumentationTargetProvider>("com.intellij.platform.backend.documentation.psiTargetProvider")
        ExtensionTestUtil.maskExtensions(point, point.extensionList.filterNot { it is CssVariablePsiDocumentationTargetProvider }, testRootDisposable)
        assertNativeDocumentation("app.css", ":root { --brand: re<caret>d; }")
    }

    fun testNativeScssColorDocumentationInVariableRemainsAvailable() {
        assertNativeDocumentation("app.scss", "\$brand: re<caret>d;")
    }

    fun testNativeLessColorDocumentationInVariableRemainsAvailable() {
        assertNativeDocumentation("app.less", "@brand: re<caret>d;")
    }

    fun testNativeColorDocumentationInOrdinaryPropertyRemainsAvailable() {
        assertNativeDocumentation("app.css", ".a { color: re<caret>d; }")
    }

    fun testNativeFunctionDocumentationInCustomPropertyRemainsAvailable() {
        assertNativeDocumentation("app.css", ":root { --brand: rg<caret>b(255, 0, 0); }")
    }

    fun testJavaScriptDocumentationRemainsAvailable() {
        assertNativeDocumentation("app.js", "/** Returns a value. @returns {string} */\nfunction sample() { return '\$brand'; }\nsam<caret>ple();")
    }

    fun testTypeScriptDocumentationRemainsAvailable() {
        assertNativeDocumentation("app.ts", "/** Returns a value. @returns {string} */\nfunction sample(): string { return '@brand'; }\nsam<caret>ple();")
    }

    fun testLessAtRulesAreNotClaimedAsVariableDocumentation() {
        val failures = mutableListOf<String>()
        for (rule in listOf("media", "supports", "container", "layer", "scope", "property", "starting-style", "font-face", "keyframes", "import")) {
            val file = myFixture.configureByText("app.less", "@$rule example { color: red; }")
            val leaf = requireNotNull(file.findElementAt(2))
            if (CssVariablePsiDocumentationTargetProvider().documentationTarget(leaf, leaf) != null) failures += rule
        }
        assertTrue("Claimed LESS at-rules: $failures", failures.isEmpty())
    }

    fun testCssFallbackArgumentDoesNotReceiveBareCustomPropertyNames() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.css", ".a { color: var(--missing, re<caret>); }")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("--red") })
    }

    fun testNestedFallbackVarStillOffersVariables() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.css", ".a { color: var(--missing, var(--re<caret>)); }")
        assertContainsElements(items.map { it.lookupString }, "--red", "--reddish")
    }

    fun testFallbackWithoutMatchingPluginTokensLeavesDocumentUnchanged() {
        val items = completeCssVariables("app.css", ".a { color: var(--missing, re<caret>); }")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("--") })
        assertEquals(".a { color: var(--missing, re); }", myFixture.editor.document.text)
    }

    fun testFallbackDoesNotOfferAnInsertionThatBecomesABareCustomProperty() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        configureProjectFile("app.css", ".a { color: var(--missing, re<caret>); }")
        val items = myFixture.completeBasic().orEmpty()
        val selected = items.firstOrNull { it.lookupString == "--red" } ?: return
        myFixture.lookup.currentItem = selected
        myFixture.finishLookup('\n')
        val inserted = myFixture.editor.document.text
        assertFalse("Completion inserted a bare property name as a color: $inserted", inserted.contains("var(--missing, --red)"))
    }

    fun testFallbackFunctionDoesNotReceiveCustomPropertyCompletions() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.css", ".a { color: var(--missing, rgb(re<caret>)); }")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("--red") })
    }

    fun testCssCompletionRemainsAvailableInHtmlStyleBlock() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.html", "<style>.a { color: var(--re<caret>); }</style>")
        assertContainsElements(items.map { it.lookupString }, "--red", "--reddish")
    }

    fun testCssCompletionRemainsAvailableInHtmlStyleAttribute() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.html", "<div style=\"color: var(--re<caret>)\"></div>")
        assertContainsElements(items.map { it.lookupString }, "--red", "--reddish")
    }

    fun testJavaScriptStringDoesNotReceiveCustomPropertyCompletions() {
        addProjectStylesheet("tokens.css", ":root { --red: red; --reddish: pink; }")
        val items = completeCssVariables("app.js", "const value = 'var(--re<caret>)';")
        assertTrue(items.toString(), items.none { it.lookupString.startsWith("--red") })
    }

    fun testDocumentationPropagatesCancellation() {
        val file = myFixture.configureByText("app.css", ":root { --brand: red; }")
        assertCancellation { CssVariableDocumentationService.generateDocumentation(file, "--brand") }
    }

    fun testImportResolutionPropagatesCancellation() {
        val file = myFixture.addFileToProject("app.css", "@import './tokens.css';")
        assertCancellation { ImportResolver.resolveDirectImports(file.virtualFile, project) }
    }

    fun testDocumentationAndHintsAvoidIndexesInDumbMode() {
        val file = myFixture.configureByText("app.css", ":root { --brand: red; }")
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertNull(CssVariableDocumentationService.generateDocumentation(file, "--brand"))
            assertNull(CssVariableDocumentationService.generateHint(file, "--brand"))
        }
    }

    private fun assertNativeDocumentation(path: String, source: String) {
        configureProjectFile(path, source)
        val targets = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
        assertTrue("No native targets for $source", targets.isNotEmpty())
        assertFalse("Plugin claimed $source: $targets", targets.any { it is CssVariableDocumentationTarget })
    }

    private fun assertCancellation(action: () -> Unit) {
        val indicator = EmptyProgressIndicator()
        try {
            ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); action() }, indicator)
            fail("Canceled editor work must propagate ProcessCanceledException")
        } catch (_: ProcessCanceledException) {
            // Expected platform control flow.
        }
    }
}
