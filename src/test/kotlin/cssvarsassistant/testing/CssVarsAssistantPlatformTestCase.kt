package cssvarsassistant.testing

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

private const val ENTRY_SEPARATOR = "|||"

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

        CssVarKeyCache.get(project).clear()
        ImportCache.get(project).clear()
        CssVarCompletionCache.clearCaches()
        PreprocessorUtil.clearCache()
        ScopeUtil.clearCache(project)
    }

    protected fun addProjectStylesheet(path: String, text: String) {
        myFixture.addFileToProject(path, text.trimIndent())
    }

    protected fun completeCssVariables(path: String, text: String): List<RenderedLookup> {
        myFixture.configureByText(path, text.trimIndent())
        return myFixture.completeBasic()
            ?.map(::renderLookup)
            .orEmpty()
    }

    protected fun readIndexedCssEntries(
        variableName: String,
        scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)
    ): List<IndexedCssEntry> {
        return FileBasedIndex.getInstance()
            .getValues(CSS_VARIABLE_INDEXER_NAME, variableName, scope)
            .flatMap { it.split(ENTRY_SEPARATOR) }
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(DELIMITER, limit = 3)
                if (parts.size < 2) {
                    null
                } else {
                    IndexedCssEntry(
                        context = parts[0],
                        value = parts[1],
                        comment = parts.getOrElse(2) { "" }
                    )
                }
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
}
