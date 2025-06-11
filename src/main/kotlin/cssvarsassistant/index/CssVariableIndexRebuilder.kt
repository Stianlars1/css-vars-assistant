package cssvarsassistant.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.project.ProjectManager
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.completion.CompletionDataCache
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

/**
 * PERFORMANCE FIX: Enhanced cache clearing for all performance-related caches
 */
object CssVariableIndexRebuilder {

    private val logger = Logger.getInstance(CssVariableIndexRebuilder::class.java)

    @JvmStatic
    fun forceRebuild() {
        logger.info("Force rebuilding CSS Variable index and clearing all caches")

        FileBasedIndex.getInstance().requestRebuild(CSS_VARIABLE_INDEXER_NAME)

        // PERFORMANCE FIX: Clear all performance-related caches for all open projects
        ProjectManager.getInstance().openProjects.forEach { project ->
            logger.debug("Clearing caches for project: ${project.name}")

            // Clear key cache
            CssVarKeyCache.get(project).clear()

            // PERFORMANCE FIX: Clear new completion data cache
            CompletionDataCache.get(project).clear()

            // Clear existing caches
            CssVarCompletionCache.clearCaches()

            // PERFORMANCE FIX: Clear scope caches
            ScopeUtil.clearCaches()
        }

        // Clear global preprocessor cache
        PreprocessorUtil.clearCache()

        logger.info("CSS Variable index rebuild and cache clearing completed")
    }
}