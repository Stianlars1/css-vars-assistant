package cssvarsassistant.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CompletionDataCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ImportCache {

    private val logger = Logger.getInstance(ImportCache::class.java)

    /** project → set of imported VirtualFiles */
    private val map = ConcurrentHashMap<Project, MutableSet<VirtualFile>>()

    fun add(project: Project, files: Collection<VirtualFile>) {
        val wasEmpty = map[project]?.isEmpty() ?: true
        map.computeIfAbsent(project) { ConcurrentHashMap.newKeySet() }.addAll(files)

        if (wasEmpty && files.isNotEmpty()) {
            logger.debug("Import cache updated for ${project.name}, clearing related caches")
            PreprocessorUtil.clearCache()
            CssVarCompletionCache.clearCaches()
            // PERFORMANCE FIX: Clear completion data cache when imports change
            CompletionDataCache.get(project).clear()
            ScopeUtil.clearCaches()
        }
    }

    fun get(project: Project): Set<VirtualFile> = map[project] ?: emptySet()

    /**
     * PERFORMANCE FIX: Enhanced cache clearing when imports are updated
     */
    fun clear(project: Project) {
        logger.info("Clearing import cache for project: ${project.name}")

        map[project]?.clear()

        // PERFORMANCE FIX: Clear all related performance caches
        PreprocessorUtil.clearCache()
        CssVarCompletionCache.clearCaches()
        CompletionDataCache.get(project).clear()
        ScopeUtil.clearCaches()
    }

    companion object {
        @JvmStatic
        fun get(project: Project): ImportCache =
            project.getService(ImportCache::class.java)
    }
}