package cssvarsassistant.util

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import java.util.concurrent.ConcurrentHashMap

object ScopeUtil {
    private val preprocessorScopes = ConcurrentHashMap<Project, GlobalSearchScope>()

    /**
     * Scope used for CSS variable indexing. This does not change frequently and
     * therefore does not need per-call recomputation.
     */
    fun effectiveCssIndexingScope(project: Project, settings: CssVarsAssistantSettings): GlobalSearchScope =
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

    /**
     * Returns the current scope for resolving preprocessor variables. Cached to
     * provide a stable hashCode for use in caches.
     */
    fun currentPreprocessorScope(project: Project): GlobalSearchScope =
        preprocessorScopes.computeIfAbsent(project) { computePreprocessorScope(project) }

    private fun computePreprocessorScope(project: Project): GlobalSearchScope {
        val settings = CssVarsAssistantSettings.getInstance()
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

    fun clearCache(project: Project) {
        preprocessorScopes.remove(project)
    }

    fun clearAll() = preprocessorScopes.clear()
}
