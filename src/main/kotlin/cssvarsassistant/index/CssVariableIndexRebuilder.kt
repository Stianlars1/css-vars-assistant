// CssVariableIndexRebuilder.kt - Enhanced version
package cssvarsassistant.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

object CssVariableIndexRebuilder {
    private val LOG = Logger.getInstance(CssVariableIndexRebuilder::class.java)

    @JvmStatic
    fun forceRebuild() {
        try {
            LOG.info("🔄 Starting comprehensive index rebuild...")

            val fileBasedIndex = FileBasedIndex.getInstance()

            // Request rebuild of both indexes
            fileBasedIndex.requestRebuild(CSS_VARIABLE_INDEXER_NAME)
            fileBasedIndex.requestRebuild(PREPROCESSOR_VARIABLE_INDEX_NAME)

            // Clear all related caches for all open projects
            ProjectManager.getInstance().openProjects.forEach { project ->
                try {
                    if (!project.isDisposed) {
                        CssVarKeyCache.get(project).clear()
                        ImportCache.get(project).clear()
                        ScopeUtil.clearCache(project)
                    }
                } catch (e: Exception) {
                    LOG.warn("Error clearing caches for project ${project.name}", e)
                }
            }

            // Clear static caches
            PreprocessorUtil.clearCache()
            ScopeUtil.clearAll()

            LOG.info("✅ Comprehensive index rebuild completed")

        } catch (e: Exception) {
            LOG.error("❌ Error during force rebuild", e)
        }
    }
}
