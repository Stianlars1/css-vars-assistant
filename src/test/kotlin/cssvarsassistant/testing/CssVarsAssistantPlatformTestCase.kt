package cssvarsassistant.testing

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.CssVariableIndexValueCodec
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

abstract class CssVarsAssistantPlatformTestCase : BasePlatformTestCase() {

    data class IndexedCssEntry(
        val context: String,
        val value: String,
        val comment: String
    )

    data class RenderedLookup(
        val lookupString: String,
        val itemText: String?,
        val tailText: String?,
        val typeText: String?
    )

    override fun setUp() {
        super.setUp()

        val settings = CssVarsAssistantSettings.getInstance()
        settings.indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
        settings.maxImportDepth = CssVarsAssistantSettings.MAX_IMPORT_DEPTH
        settings.showContextValues = true
        settings.allowIdeCompletions = true
        settings.sortingOrder = CssVarsAssistantSettings.SortingOrder.ASC

        clearPluginCaches()
    }

    protected fun addProjectStylesheet(path: String, text: String) {
        myFixture.addFileToProject(path, text.trimIndent())
    }

    protected fun updateSettings(block: CssVarsAssistantSettings.() -> Unit) {
        CssVarsAssistantSettings.getInstance().apply(block)
        clearPluginCaches()
    }

    protected fun completeCssVariables(path: String, text: String): List<RenderedLookup> {
        myFixture.configureByText(path, text.trimIndent())
        return myFixture.completeBasic()
            ?.map(::renderLookup)
            .orEmpty()
    }

    protected fun completeCssVariablesInProjectFile(path: String, text: String): List<RenderedLookup> {
        configureProjectFile(path, text)
        return myFixture.completeBasic()
            ?.map(::renderLookup)
            .orEmpty()
    }

    protected fun configureProjectFile(path: String, text: String) {
        val normalizedText = text.trimIndent()
        val caretMarker = "<caret>"
        val caretOffset = normalizedText.indexOf(caretMarker)
        val fileText = normalizedText.replace(caretMarker, "")
        val file = myFixture.addFileToProject(path, fileText).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        if (caretOffset >= 0) {
            myFixture.editor.caretModel.moveToOffset(caretOffset)
        }
    }

    protected fun readIndexedCssEntries(
        variableName: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)
    ): List<IndexedCssEntry> {
        return FileBasedIndex.getInstance()
            .getValues(CSS_VARIABLE_INDEXER_NAME, variableName, scope)
            .let(CssVariableIndexValueCodec::decode)
            .map { entry ->
                IndexedCssEntry(
                    context = entry.context,
                    value = entry.value,
                    comment = entry.comment
                )
            }
    }

    private fun renderLookup(element: LookupElement): RenderedLookup {
        val presentation = LookupElementPresentation()
        element.renderElement(presentation)
        return RenderedLookup(
            lookupString = element.lookupString,
            itemText = presentation.itemText,
            tailText = presentation.tailText,
            typeText = presentation.typeText
        )
    }

    private fun clearPluginCaches() {
        CssVarKeyCache.get(project).clear()
        ImportCache.get(project).clear()
        PreprocessorUtil.clearCache(project)
        ScopeUtil.clearCache(project)
    }
}
