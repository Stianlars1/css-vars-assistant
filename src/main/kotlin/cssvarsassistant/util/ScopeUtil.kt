package cssvarsassistant.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.CollectionFactory
import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings

/**
 * Helper methods for choosing the correct [GlobalSearchScope] when
 * looking up CSS or pre-processor variables.
 */
object ScopeUtil {

    private data class ScopeFingerprint(
        val indexingScope: CssVarsAssistantSettings.IndexingScope,
        val importedPaths: List<String>
    )

    private data class CachedPreprocessorScope(
        val fingerprint: ScopeFingerprint,
        val scope: GlobalSearchScope
    )

    /**
     * Prosjekt → pre-processor-scope cache.
     *
     * Bruker `CollectionFactory.createConcurrentWeakKeySoftValueMap` som
     * lager en concurrent weak-key / soft-value-map. Dermed forsvinner
     * oppføringen automatisk når [Project] blir garbage-collected eller
     * pluginen dynamisk avlastes.
     */
    private val preprocessorScopes =
        CollectionFactory.createConcurrentWeakKeySoftValueMap<Project, CachedPreprocessorScope>()

    /* ---------- CSS scopes ------------------------------------------------ */

    fun effectiveCssIndexingScope(
        project: Project,
        settings: CssVarsAssistantSettings
    ): GlobalSearchScope =
        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY ->
                GlobalSearchScope.projectScope(project)

            CssVarsAssistantSettings.IndexingScope.GLOBAL ->
                GlobalSearchScope.allScope(project)

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                val extra = ImportCache.get(project).getOrBuild(settings.maxImportDepth)
                if (extra.isEmpty())
                    GlobalSearchScope.projectScope(project)
                else
                    GlobalSearchScope.projectScope(project)
                        .uniteWith(GlobalSearchScope.filesScope(project, extra))
            }
    }

    /* ---------- Pre-processor scopes -------------------------------------- */

    fun currentPreprocessorScope(project: Project): GlobalSearchScope {
        val settings = CssVarsAssistantSettings.getInstance()
        val importedFiles = when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS ->
                ImportCache.get(project).getOrBuild(settings.maxImportDepth)

            else -> emptySet()
        }
        val fingerprint = ScopeFingerprint(
            settings.indexingScope,
            importedFiles.asSequence().map(VirtualFile::getPath).sorted().toList()
        )

        preprocessorScopes[project]?.let { cached ->
            if (cached.fingerprint == fingerprint) {
                return cached.scope
            }
        }
        val scope = when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY ->
                projectFilesScopeExcludingNodeModules(project)

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                val base = projectFilesScopeExcludingNodeModules(project)
                if (importedFiles.isEmpty()) {
                    base
                } else {
                    base.uniteWith(GlobalSearchScope.filesScope(project, importedFiles))
                }
            }

            CssVarsAssistantSettings.IndexingScope.GLOBAL ->
                GlobalSearchScope.allScope(project)
        }
        preprocessorScopes[project] = CachedPreprocessorScope(fingerprint, scope)
        return scope
    }

    /* ---------- Cache maintenance ----------------------------------------- */

    fun clearCache(project: Project) {
        preprocessorScopes.remove(project)
    }

    fun clearAll() = preprocessorScopes.clear()

    private fun projectFilesScopeExcludingNodeModules(project: Project): GlobalSearchScope {
        val baseScope = GlobalSearchScope.projectScope(project)
        return object : GlobalSearchScope(project) {
            override fun contains(file: VirtualFile): Boolean =
                baseScope.contains(file) && !file.path.contains("/node_modules/")

            override fun compare(file1: VirtualFile, file2: VirtualFile): Int =
                baseScope.compare(file1, file2)

            override fun isSearchInModuleContent(aModule: Module): Boolean =
                baseScope.isSearchInModuleContent(aModule)

            override fun isSearchInLibraries(): Boolean = false
        }
    }
}
