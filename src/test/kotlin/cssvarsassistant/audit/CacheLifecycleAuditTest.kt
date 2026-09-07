package cssvarsassistant.audit

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase
import cssvarsassistant.util.PreprocessorUtil

class CacheLifecycleAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testImportCacheAddsAllFilesInBatch() {
        val first = myFixture.addFileToProject("first.css", "").virtualFile
        val second = myFixture.addFileToProject("second.css", "").virtualFile
        val cache = ImportCache.get(project)
        cache.add(listOf(first, second))
        assertEquals(setOf(first, second), cache.get())
    }

    fun testKeyCacheSeesNewlyIndexedVariableWithoutManualReset() {
        addProjectStylesheet("first.css", ":root { --first: red; }")
        val scope = GlobalSearchScope.projectScope(project)
        val cache = CssVarKeyCache.get(project)
        assertFalse(readCssEntries("--first").isEmpty())
        assertContainsElements(cache.keys(scope), "--first")
        addProjectStylesheet("second.css", ":root { --second: blue; }")
        assertFalse(readCssEntries("--second").isEmpty())
        assertContainsElements(cache.keys(scope), "--second")
    }

    fun testResolvedLessValueRefreshesAfterSavedEdit() {
        val file = myFixture.addFileToProject("tokens.less", "@brand: red;").virtualFile
        val scope = GlobalSearchScope.projectScope(project)
        assertEquals("red", PreprocessorUtil.resolveVariable(project, "@brand", scope))
        saveText(file, "@brand: blue;")
        assertEquals(listOf("blue"), readPreprocessorValues("@brand"))
        assertEquals("blue", PreprocessorUtil.resolveVariable(project, "@brand", scope))
    }

    fun testImportClosureRefreshesAfterSavedEdit() {
        val first = myFixture.addFileToProject("first.css", ":root { --first: red; }").virtualFile
        val second = myFixture.addFileToProject("second.css", ":root { --second: blue; }").virtualFile
        val entry = myFixture.addFileToProject("app.css", "@import './first.css';").virtualFile
        val cache = ImportCache.get(project)
        assertEquals(setOf(first), cache.getOrBuild(5))
        saveText(entry, "@import './second.css';")
        assertEquals(setOf(second), cache.getOrBuild(5))
    }

    fun testImportedCssValueRefreshesWhenOnlyDependencyChanges() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        val tokens = myFixture.addFileToProject("node_modules/vendor/tokens.css", ":root { --brand: red; }").virtualFile
        myFixture.addFileToProject("app.css", "@import 'vendor/tokens.css';")
        assertEquals(listOf("red"), readCssEntries("--brand").map { it.value })
        saveText(tokens, ":root { --brand: blue; }")
        assertEquals(listOf("blue"), readCssEntries("--brand").map { it.value })
    }

    private fun saveText(file: VirtualFile, text: String) {
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }
}
