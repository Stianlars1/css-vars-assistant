package cssvarsassistant.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.index.ImportCache
import com.intellij.openapi.components.Service
import cssvarsassistant.settings.CssVarsAssistantSettings

/**
 * Helper methods for choosing the correct [GlobalSearchScope] when
 * looking up CSS or pre-processor variables.
 */
object ScopeUtil {

    fun effectiveCssIndexingScope(
        project: Project,
        settings: CssVarsAssistantSettings
    ): GlobalSearchScope =
        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY ->
                projectFilesScopeExcludingNodeModules(project)

            CssVarsAssistantSettings.IndexingScope.GLOBAL -> {
                val base = GlobalSearchScope.allScope(project)
                val extra = ImportCache.get(project).getOrBuild(settings.maxImportDepth)
                if (extra.isEmpty()) base else base.uniteWith(GlobalSearchScope.filesScope(project, extra))
            }

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                // Use project files *excluding* node_modules as the base, then
                // union in whichever node_modules files actually got walked via
                // `@import` resolution. Without this, an uninitialised import
                // cache fell back to plain projectScope which includes every
                // node_modules CSS file on disk — thousands of irrelevant
                // --foo entries polluted completion.
                val base = projectFilesScopeExcludingNodeModules(project)
                val extra = ImportCache.get(project).getOrBuild(settings.maxImportDepth)
                if (extra.isEmpty()) base
                else base.uniteWith(GlobalSearchScope.filesScope(project, extra))
            }
    }

    /* ---------- Pre-processor scopes -------------------------------------- */

    fun currentPreprocessorScope(project: Project): GlobalSearchScope =
        effectiveCssIndexingScope(project, CssVarsAssistantSettings.getInstance())

    private fun projectFilesScopeExcludingNodeModules(project: Project): GlobalSearchScope =
        project.getService(ProjectStylesheetScope::class.java).scope
}

@Service(Service.Level.PROJECT)
internal class ProjectStylesheetScope(project: Project) {
    val scope: GlobalSearchScope = object : GlobalSearchScope(project) {
        private val base = GlobalSearchScope.projectScope(project)
        override fun contains(file: VirtualFile): Boolean = base.contains(file) && !file.path.contains("/node_modules/")
        override fun compare(file1: VirtualFile, file2: VirtualFile): Int = base.compare(file1, file2)
        override fun isSearchInModuleContent(module: Module): Boolean = base.isSearchInModuleContent(module)
        override fun isSearchInLibraries(): Boolean = false
    }
}
