package cssvarsassistant.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import java.util.concurrent.ConcurrentHashMap

/**
 * PERFORMANCE OPTIMIZED: Eliminates file I/O during completion by using
 * pre-indexed data instead of scanning project files
 */
object PreprocessorUtil {
    private val LOG = Logger.getInstance(PreprocessorUtil::class.java)
    private val cache = ConcurrentHashMap<String, String?>()

    /**
     * PERFORMANCE FIX: Resolve preprocessor variables using pre-indexed data
     * instead of expensive file I/O operations
     */
    fun resolveVariable(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet()
    ): String? {
        if (varName in visited) return null

        // PERFORMANCE FIX: Use stable cache key instead of hashCode()
        val cacheKey = "${project.name}_${varName}_${scope.displayName}"
        cache[cacheKey]?.let { return it }

        try {
            ProgressManager.checkCanceled()

            // PERFORMANCE FIX: Use CSS Variable Index instead of file I/O
            val result = resolveFromIndex(project, varName, scope, visited)

            if (result != null) {
                cache[cacheKey] = result
            }

            return result
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving preprocessor variable: $varName", e)
            return null
        }
    }

    /**
     * PERFORMANCE FIX: Resolve using index data instead of reading files from disk
     * This eliminates the expensive FilenameIndex.getAllFilesByExt() + file reading
     */
    private fun resolveFromIndex(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String>
    ): String? {
        // Try different variable name formats that might be in the index
        val candidateNames = listOf(
            "--$varName",  // CSS custom property
            "@$varName",   // LESS variable
            "\$$varName"   // SCSS variable
        )

        for (candidateName in candidateNames) {
            try {
                ProgressManager.checkCanceled()

                val values = FileBasedIndex.getInstance()
                    .getValues(CSS_VARIABLE_INDEXER_NAME, candidateName, scope)
                    .flatMap { it.split("|||") }
                    .filter { it.isNotBlank() }

                if (values.isNotEmpty()) {
                    // Get the default value or first available value
                    val defaultValue = values.mapNotNull {
                        val parts = it.split(DELIMITER, limit = 3)
                        if (parts.size >= 2) parts[0] to parts[1] else null
                    }.let { pairs ->
                        pairs.find { it.first == "default" }?.second ?: pairs.firstOrNull()?.second
                    }

                    if (defaultValue != null) {
                        // Check if this value references another variable
                        val refMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(defaultValue.trim())
                        if (refMatch != null && refMatch.groupValues[1] !in visited) {
                            // Recursively resolve the referenced variable
                            return resolveFromIndex(project, refMatch.groupValues[1], scope, visited + varName)
                        }
                        return defaultValue
                    }
                }
            } catch (e: Exception) {
                LOG.debug("Error checking candidate name $candidateName", e)
            }
        }

        return null
    }

    fun clearCache() {
        LOG.info("Clearing preprocessor variable cache")
        cache.clear()
    }
}