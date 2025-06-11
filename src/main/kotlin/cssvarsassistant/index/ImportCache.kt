package cssvarsassistant.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ImportCache {

    /** project → set of imported VirtualFiles */
    private val map = ConcurrentHashMap<Project, MutableSet<VirtualFile>>()

    fun add(project: Project, files: Collection<VirtualFile>) {
        val wasEmpty = map[project]?.isEmpty() ?: true
        map.computeIfAbsent(project) { ConcurrentHashMap.newKeySet() }.addAll(files)

        if (wasEmpty && files.isNotEmpty()) {
            PreprocessorUtil.clearCache()
            CssVarCompletionCache.clearCaches()
            CssVarKeyCache.get(project).clear()
            ScopeUtil.clearCache(project)
        }
    }

    fun get(project: Project): Set<VirtualFile> = map[project] ?: emptySet()

    fun clear(project: Project) {
        map[project]?.clear()
        // Clear caches when imports change
        PreprocessorUtil.clearCache()
        CssVarCompletionCache.clearCaches()
        CssVarKeyCache.get(project).clear()
        ScopeUtil.clearCache(project)
    }

    companion object {
        @JvmStatic
        fun get(project: Project): ImportCache =
            project.getService(ImportCache::class.java)
    }
}
