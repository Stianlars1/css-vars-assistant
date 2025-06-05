package cssvarsassistant.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.project.IndexNotReadyException
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME

/**
 * Caches the list of CSS variable names for a project to avoid expensive
 * FileBasedIndex lookups on every completion invocation.
 */
@Service(Service.Level.PROJECT)
class CssVarKeyCache(private val project: Project) {
    @Volatile
    private var keys: List<String>? = null

    /** Returns all variable names, loading them once per project. */
    fun getKeys(): List<String> {
        val cached = keys
        if (cached != null) return cached

        ProgressManager.checkCanceled()
        val loaded = try {
            FileBasedIndex.getInstance()
                .getAllKeys(CSS_VARIABLE_INDEXER_NAME, project)
                .toList()
        } catch (_: IndexNotReadyException) {
            return cached ?: emptyList()
        }
        if (loaded.isEmpty()) return cached ?: emptyList()

        keys = loaded
        return loaded
    }

    /** Clears the cached keys so they will be reloaded on next request. */
    fun clear() {
        keys = null
    }

    companion object {
        @JvmStatic
        fun get(project: Project): CssVarKeyCache = project.getService(CssVarKeyCache::class.java)
    }
}