package cssvarsassistant.index

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.completion.CssVarKeyCache
import com.intellij.openapi.project.DumbService
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ImportCache {

    /** project → set of imported VirtualFiles */
    private val map = ConcurrentHashMap<Project, MutableSet<VirtualFile>>()

    fun add(project: Project, files: Collection<VirtualFile>) {
        if (files.isEmpty()) return

        val set = map.computeIfAbsent(project) { ConcurrentHashMap.newKeySet() }
        val before = set.size
        set.addAll(files)

        if (set.size > before) {
            val dumb = DumbService.getInstance(project)
            val clearAction = {
                PreprocessorUtil.clearCache()
                CssVarCompletionCache.clearCaches()
                CssVarKeyCache.get(project).clear()
            }
            if (dumb.isDumb) {
                dumb.runWhenSmart(clearAction)
            } else {
                clearAction()
            }
        }
    }

    fun get(project: Project): Set<VirtualFile> = map[project] ?: emptySet()

    fun clear(project: Project) {
        map[project]?.clear()
        // FIXED: Clear both caches when imports are cleared
        PreprocessorUtil.clearCache()
        CssVarCompletionCache.clearCaches()
        CssVarKeyCache.get(project).clear()
    }

    companion object {
        @JvmStatic
        fun get(project: Project): ImportCache =
            project.getService(ImportCache::class.java)
    }
}