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
    private val arithmeticRegex = Regex("""\(\s*[@$]([\w-]+)\s*\*\s*(-?\d+(?:\.\d+)?)\s*\)""")
    private val numUnitRegex = Regex("""(-?\d+(?:\.\d+)?)(px|rem|em|%|vh|vw|pt)""")

    internal fun applyArithmetic(baseValue: String, multiplier: Double): String? {
        val match = numUnitRegex.find(baseValue) ?: return null
        val num = match.groupValues[1].toDouble()
        val unit = match.groupValues[2]
        val result = num * multiplier
        val formatted = if (result % 1.0 == 0.0) result.toInt().toString() else result.toString()
        return "$formatted$unit"
    }

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

                // Handle arithmetic like (@ffe-spacing * 1.5)
                val mathMatch = arithmeticRegex.find(value)
                if (mathMatch != null) {
                    val baseVar = mathMatch.groupValues[1]
                    val multiplier = mathMatch.groupValues[2].toDoubleOrNull() ?: continue
                    val baseValue = resolveVariable(project, baseVar, scope, visited + varName)
                    if (baseValue != null) {
                        applyArithmetic(baseValue, multiplier)?.let { result ->
                            cache[key] = result
                            return result
                        }
                    }
                }

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
