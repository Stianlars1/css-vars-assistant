package cssvarsassistant.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * PERFORMANCE OPTIMIZED: Caches scope computation to eliminate expensive
 * scope recomputation during completion
 */
object ScopeUtil {

    private val logger = Logger.getInstance(ScopeUtil::class.java)

    // Cache for CSS indexing scope (used during indexing and completion)
    private val cssIndexingScopeCache = ConcurrentHashMap<String, GlobalSearchScope>()

    // Cache for preprocessor scope (used during resolution)
    private val preprocessorScopeCache = ConcurrentHashMap<String, GlobalSearchScope>()

    /**
     * PERFORMANCE FIX: Returns stable, cached CSS indexing scope
     * Uses string-based cache key for stability across calls
     */
    fun getStableCssIndexingScope(project: Project, settings: CssVarsAssistantSettings): GlobalSearchScope {
        val importFiles = ImportCache.get(project).get(project)
        val cacheKey = buildCacheKey(project, settings, importFiles.size)

        return cssIndexingScopeCache.computeIfAbsent(cacheKey) {
            logger.debug("Computing CSS indexing scope for key: $cacheKey")
            computeCssIndexingScope(project, settings)
        }
    }

    /**
     * For CSS variable indexing (FileBasedIndex) - respects import restrictions
     * PERFORMANCE FIX: Now returns cached scope when possible
     */
    fun effectiveCssIndexingScope(project: Project, settings: CssVarsAssistantSettings): GlobalSearchScope =
        getStableCssIndexingScope(project, settings)

    /**
     * PERFORMANCE FIX: Cached preprocessor scope with stable cache keys
     */
    fun getStablePreprocessorScope(project: Project): GlobalSearchScope {
        val settings = CssVarsAssistantSettings.getInstance()
        val importFiles = ImportCache.get(project).get(project)
        val cacheKey = buildPreprocessorCacheKey(project, settings, importFiles.size)

        return preprocessorScopeCache.computeIfAbsent(cacheKey) {
            logger.debug("Computing preprocessor scope for key: $cacheKey")
            computePreprocessorScope(project, settings)
        }
    }

    /**
     * For preprocessor resolution - always includes libraries
     * PERFORMANCE FIX: Now uses cached scope
     */
    fun currentPreprocessorScope(project: Project): GlobalSearchScope {
        return getStablePreprocessorScope(project)
    }

    private fun computeCssIndexingScope(project: Project, settings: CssVarsAssistantSettings): GlobalSearchScope =
        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY ->
                GlobalSearchScope.projectScope(project)

            CssVarsAssistantSettings.IndexingScope.GLOBAL ->
                GlobalSearchScope.allScope(project)

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                val extra = ImportCache.get(project).get(project)
                if (extra.isEmpty())
                    GlobalSearchScope.projectScope(project)
                else
                    GlobalSearchScope.projectScope(project)
                        .uniteWith(GlobalSearchScope.filesScope(project, extra))
            }
        }

    private fun computePreprocessorScope(project: Project, settings: CssVarsAssistantSettings): GlobalSearchScope {
        val projectRoots = GlobalSearchScope.projectScope(project)
        val libraryRoots = ProjectScope.getLibrariesScope(project)

        return when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.GLOBAL ->
                GlobalSearchScope.allScope(project)

            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY ->
                projectRoots.uniteWith(libraryRoots)

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                val extra = ImportCache.get(project).get(project)
                val base = projectRoots.uniteWith(libraryRoots)
                if (extra.isEmpty()) base
                else base.uniteWith(GlobalSearchScope.filesScope(project, extra))
            }
        }
    }

    /**
     * PERFORMANCE FIX: Build stable cache keys based on project state
     */
    private fun buildCacheKey(project: Project, settings: CssVarsAssistantSettings, importCount: Int): String {
        return "${project.name}_${settings.indexingScope.name}_${settings.maxImportDepth}_imports:$importCount"
    }

    private fun buildPreprocessorCacheKey(project: Project, settings: CssVarsAssistantSettings, importCount: Int): String {
        return "preprocessor_${project.name}_${settings.indexingScope.name}_imports:$importCount"
    }

    /**
     * Clear all caches - call when settings change or imports are updated
     */
    fun clearCaches() {
        logger.info("Clearing all scope caches")
        cssIndexingScopeCache.clear()
        preprocessorScopeCache.clear()
    }
}