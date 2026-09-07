package cssvarsassistant.index

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.documentation.VariableResolver
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase
import cssvarsassistant.util.ScopeUtil

class SourceAwareLookupTest : CssVarsAssistantPlatformTestCase() {
    fun testUnsavedImportedCssChangesAreVisible() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        val dependency = myFixture.addFileToProject("node_modules/vendor/tokens.css", ":root { --brand: red; }").virtualFile
        addProjectStylesheet("app.css", "@import 'vendor/tokens.css';")
        assertEquals(listOf("red"), readCssEntries("--brand").map { it.value })
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(dependency))
        WriteCommandAction.runWriteCommandAction(project) { document.setText(":root { --brand: blue; }") }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertEquals(listOf("blue"), readCssEntries("--brand").map { it.value })
    }

    fun testDeletedImportedFileDoesNotLeaveGhostValues() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        val dependency = myFixture.addFileToProject("node_modules/vendor/tokens.css", ":root { --brand: red; }").virtualFile
        addProjectStylesheet("app.css", "@import 'vendor/tokens.css';")
        assertEquals(listOf("red"), readCssEntries("--brand").map { it.value })
        WriteCommandAction.runWriteCommandAction(project) { dependency.delete(this) }
        assertTrue(readCssEntries("--brand").isEmpty())
    }

    fun testPreviouslyMissingImportAppearsWithoutReset() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        addProjectStylesheet("app.css", "@import 'vendor/tokens.css';")
        assertTrue(readCssEntries("--brand").isEmpty())
        addProjectStylesheet("node_modules/vendor/tokens.css", ":root { --brand: red; }")
        assertEquals(listOf("red"), readCssEntries("--brand").map { it.value })
    }

    fun testPreprocessorIndexDoesNotCopyDependencyValues() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        val dependency = myFixture.addFileToProject("node_modules/vendor/tokens.less", "@brand: red;").virtualFile
        val importer = myFixture.addFileToProject("app.less", "@import 'vendor/tokens.less';").virtualFile
        assertTrue(FileBasedIndex.getInstance().getValues(PREPROCESSOR_VARIABLE_INDEX_NAME, "@brand", GlobalSearchScope.fileScope(project, importer)).isEmpty())
        val scope = ScopeUtil.currentPreprocessorScope(project)
        val actual = VariableLookup.preprocessorValues(project, "@brand", scope).single()
        assertEquals(dependency, actual.file)
        assertEquals("red", actual.declaration.value)
    }

    fun testScssValuesUseTheDeclarationBeforeEachUse() {
        val text = "${'$'}brand: red;\n.before { color: ${'$'}brand; }\n${'$'}brand: blue;\n.after { color: ${'$'}brand; }"
        val file = myFixture.addFileToProject("app.scss", text).virtualFile
        val resolver = VariableResolver(project)
        assertEquals("red", resolver.resolve("${'$'}brand", VariableLocation(file, text.indexOf("color:") + 8)).resolved)
        assertEquals("blue", resolver.resolve("${'$'}brand", VariableLocation(file, text.lastIndexOf("color:") + 8)).resolved)
    }

    fun testLessLocalAndGlobalScopesRemainDistinct() {
        val text = "@brand: red;\n.local { @brand: blue; color: @brand; }\n.global { color: @brand; }"
        val file = myFixture.addFileToProject("app.less", text).virtualFile
        val resolver = VariableResolver(project)
        assertEquals("blue", resolver.resolve("@brand", VariableLocation(file, text.indexOf("color:") + 8)).resolved)
        assertEquals("red", resolver.resolve("@brand", VariableLocation(file, text.lastIndexOf("color:") + 8)).resolved)
    }

    fun testScssDefaultDoesNotOverwriteImportedValue() {
        addProjectStylesheet("_base.scss", "${'$'}brand: red;")
        val file = myFixture.addFileToProject("app.scss", "@import './base';\n${'$'}brand: blue !default;").virtualFile
        assertEquals("red", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file)).resolved)
    }

    fun testLaterScssImportOverridesEarlierAssignment() {
        addProjectStylesheet("_base.scss", "${'$'}brand: red;")
        val file = myFixture.addFileToProject("app.scss", "${'$'}brand: blue;\n@import './base';").virtualFile
        assertEquals("red", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file)).resolved)
    }

    fun testImportedDefaultDoesNotOverwriteEarlierAssignment() {
        addProjectStylesheet("_base.scss", "${'$'}brand: red !default;")
        val file = myFixture.addFileToProject("app.scss", "${'$'}brand: blue;\n@import './base';").virtualFile
        assertEquals("blue", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file)).resolved)
    }

    fun testPrivateSassMemberIsNotExposedThroughUse() {
        addProjectStylesheet("_tokens.scss", "${'$'}_private: red;")
        val file = myFixture.addFileToProject("app.scss", "@use './tokens';").virtualFile
        assertEquals("tokens.${'$'}_private", VariableResolver(project).resolve("tokens.${'$'}_private", VariableLocation(file)).resolved)
    }

    fun testAliasKeepsItsDefiningModuleContext() {
        addProjectStylesheet("_first.scss", "${'$'}base: red;\n${'$'}brand: ${'$'}base;")
        addProjectStylesheet("_second.scss", "${'$'}base: blue;\n${'$'}brand: ${'$'}base;")
        val file = myFixture.addFileToProject("app.scss", "@use './first';\n@use './second';").virtualFile
        val resolver = VariableResolver(project)
        assertEquals("red", resolver.resolve("first.${'$'}brand", VariableLocation(file)).resolved)
        assertEquals("blue", resolver.resolve("second.${'$'}brand", VariableLocation(file)).resolved)
    }

    fun testSassReassignmentCanReferToItsPreviousValue() {
        val file = myFixture.addFileToProject("app.scss", "${'$'}brand: red;\n${'$'}brand: ${'$'}brand;").virtualFile
        assertEquals("red", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file)).resolved)
    }
}
