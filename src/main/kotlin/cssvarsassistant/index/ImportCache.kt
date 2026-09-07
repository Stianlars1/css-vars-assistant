// ImportCache.kt - Enhanced version
package cssvarsassistant.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.settings.CssVarsAssistantSettings

@Service(Service.Level.PROJECT)
class ImportCache(private val project: Project) : Disposable {
    private val LOG = Logger.getInstance(ImportCache::class.java)
    private val importedFiles = linkedSetOf<VirtualFile>()
    @Volatile
    private var initialized = false
    private var builtAt = -1L
    private var builtDepth = -1


    @Synchronized
    fun getOrBuild(maxDepth: Int): Set<VirtualFile> {
        val revision = StylesheetChanges.stamp(project)
        if (!initialized || builtAt != revision || builtDepth != maxDepth) {
            replaceInternal(ImportResolver.collectProjectImports(project, maxDepth))
            builtAt = revision
            builtDepth = maxDepth
            initialized = true
        }
        return importedFiles.toSet()
    }

    @Synchronized
    fun add(files: Collection<VirtualFile>) {
        initialized = true
        builtAt = StylesheetChanges.stamp(project)
        builtDepth = CssVarsAssistantSettings.getInstance().maxImportDepth
        if (importedFiles.addAll(files)) {
            invalidateDependentCaches()
        }
    }

    @Synchronized
    fun get(): Set<VirtualFile> = importedFiles.toSet()

    @Synchronized
    fun replace(files: Collection<VirtualFile>) {
        initialized = true
        builtAt = StylesheetChanges.stamp(project)
        builtDepth = CssVarsAssistantSettings.getInstance().maxImportDepth
        replaceInternal(files)
    }

    @Synchronized
    fun clear() {
        try {
            initialized = false
            importedFiles.clear()
            invalidateDependentCaches()
        } catch (e: Exception) {
            LOG.warn("Error clearing ImportCache", e)
        }
    }

    @Synchronized
    override fun dispose() = importedFiles.clear()

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
        cssvarsassistant.completion.CssVarKeyCache.get(project).clear()
    }
}
