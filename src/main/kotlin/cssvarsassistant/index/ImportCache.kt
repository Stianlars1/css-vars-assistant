// ImportCache.kt - Enhanced version
package cssvarsassistant.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ImportCache(private val project: Project) : Disposable {
    private val LOG = Logger.getInstance(ImportCache::class.java)
    private val importedFiles = ConcurrentHashMap.newKeySet<VirtualFile>()
    @Volatile
    private var initialized = false


    @Synchronized
    fun getOrBuild(maxDepth: Int): Set<VirtualFile> {
        if (!initialized) {
            replaceInternal(ImportResolver.collectProjectImports(project, maxDepth))
            initialized = true
        }
        return importedFiles.toSet()
    }

    @Synchronized
    fun add(files: Collection<VirtualFile>) {
        initialized = true
        if (files.any { importedFiles.add(it) }) {
            invalidateDependentCaches()
        }
    }

    fun get(): Set<VirtualFile> = importedFiles.toSet()

    @Synchronized
    fun replace(files: Collection<VirtualFile>) {
        initialized = true
        replaceInternal(files)
    }

    fun clear() {
        try {
            initialized = false
            importedFiles.clear()
            invalidateDependentCaches()
        } catch (e: Exception) {
            LOG.warn("Error clearing ImportCache", e)
        }
    }

    override fun dispose() {
        try {
            clear()
            LOG.debug("ImportCache disposed for project: ${project.name}")
        } catch (e: Exception) {
            LOG.warn("Error disposing ImportCache", e)
        }
    }

    companion object {
        @JvmStatic
        fun get(project: Project): ImportCache =
            project.getService(ImportCache::class.java)
    }

    private fun replaceInternal(files: Collection<VirtualFile>) {
        val newFiles = files.toSet()
        if (importedFiles == newFiles) {
            return
        }

        importedFiles.clear()
        importedFiles.addAll(newFiles)
        invalidateDependentCaches()
    }

    private fun invalidateDependentCaches() {
        PreprocessorUtil.clearCache(project)
        ScopeUtil.clearCache(project)
    }
}
