package cssvarsassistant.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.PREPROCESSOR_VARIABLE_INDEX_NAME

/**
 * Utility for resolving LESS/SCSS variables across the project.
 * Uses a FileBasedIndex instead of scanning files on each request.
 */
object PreprocessorUtil {
    private val LOG = Logger.getInstance(PreprocessorUtil::class.java)
    private val cache = mutableMapOf<Triple<Project, String, Int>, String?>()

    /**
     * Resolves the value of a preprocessor variable (@foo or $foo) within [scope].
     * Recursively resolves references to other variables.
     */
    fun resolveVariable(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet()
    ): String? {
        ProgressManager.checkCanceled()
        if (varName in visited) return null
        val key = Triple(project, varName, scope.hashCode())
        cache[key]?.let { return it }

        return try {
            val values = FileBasedIndex.getInstance()
                .getValues(PREPROCESSOR_VARIABLE_INDEX_NAME, varName, scope)

            if (values.isEmpty()) return null

            for (value in values) {
                ProgressManager.checkCanceled()
                val refMatch = Regex("^[\\s]*[@$]([\\w-]+)").find(value)
                val resolved = if (refMatch != null) {
                    resolveVariable(project, refMatch.groupValues[1], scope, visited + varName)
                        ?: value
                } else value
                cache[key] = resolved
                return resolved
            }
            null
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving preprocessor variable: $varName", e)
            null
        }
    }

    fun clearCache() = cache.clear()
}
