package cssvarsassistant.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.StylesheetChanges
import cssvarsassistant.index.VariableLookup
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ScopeUtil

@Service(Service.Level.PROJECT)
class CssVarKeyCache(private val project: Project) : Disposable {
    private data class Revision(val changes: Long, val index: Long, val mode: CssVarsAssistantSettings.IndexingScope, val depth: Int)
    private data class Cached(val revision: Revision, val keys: List<String>)
    private val values = CollectionFactory.createConcurrentWeakKeySoftValueMap<GlobalSearchScope, Cached>()

    fun keys(scope: GlobalSearchScope): List<String> {
        ProgressManager.checkCanceled()
        val settings = CssVarsAssistantSettings.getInstance()
        val effectiveScope = ScopeUtil.effectiveCssIndexingScope(project, settings).intersectWith(scope)
        val revision = Revision(StylesheetChanges.stamp(project), FileBasedIndex.getInstance().getIndexModificationStamp(CSS_VARIABLE_INDEXER_NAME, project), settings.indexingScope, settings.maxImportDepth)
        values[scope]?.let { if (it.revision == revision) return it.keys }
        val keys = VariableLookup.cssKeys(project, effectiveScope)
        values[scope] = Cached(revision, keys)
        return keys
    }

    fun clear() = values.clear()
    override fun dispose() = clear()
    companion object {
        @JvmStatic fun get(project: Project): CssVarKeyCache = project.getService(CssVarKeyCache::class.java)
    }
}
