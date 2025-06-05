package cssvarsassistant.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.index.ImportCache
import cssvarsassistant.util.ArithmeticEvaluator

object PreprocessorUtil {
    private val LOG = Logger.getInstance(PreprocessorUtil::class.java)
    private val cache = mutableMapOf<Triple<Project, String, Int>, String?>()

    /**
     * Finn verdien til en LESS/SCSS/CSS-variabel (@foo, $foo, --foo) uansett filnavn.
     */
    fun resolveVariable(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet()
    ): String? {
        if (varName in visited) return null          // syklussikring
        val key = Triple(project, varName, scope.hashCode())
        cache[key]?.let { return it }

        try {
            for (ext in listOf("less", "scss", "sass", "css")) {
                // Check for cancellation before expensive operations
                ProgressManager.checkCanceled()

                // Files from standard indexes
                val indexedFiles = FilenameIndex.getAllFilesByExt(project, ext, scope)

                // Files discovered via import resolution but not indexed
                val extraFiles = ImportCache.get(project)
                    .get(project)
                    .filter { it.extension?.equals(ext, ignoreCase = true) == true }

                val files = (indexedFiles + extraFiles).distinct()

                for (vf in files) {
                    // Check for cancellation in loops
                    ProgressManager.checkCanceled()

                    val text = String(vf.contentsToByteArray())

                    // LESS / SCSS / CSS matcher
                    val value = when {
                        Regex("""@${Regex.escape(varName)}\s*:\s*([^;]+);""")
                            .find(text) != null ->
                            Regex("""@${Regex.escape(varName)}\s*:\s*([^;]+);""")
                                .find(text)!!.groupValues[1].trim()

                        Regex("""\$${Regex.escape(varName)}\s*:\s*([^;]+);""")
                            .find(text) != null ->
                            Regex("""\$${Regex.escape(varName)}\s*:\s*([^;]+);""")
                                .find(text)!!.groupValues[1].trim()

                        Regex("""--${Regex.escape(varName)}\s*:\s*([^;]+);""")
                            .find(text) != null ->
                            Regex("""--${Regex.escape(varName)}\s*:\s*([^;]+);""")
                                .find(text)!!.groupValues[1].trim()

                        else -> null
                    }

                    if (value != null) {
                        var replaced: String = value
                        var changed = true
                        val refRegex = Regex("[@$]([\\w-]+)")

                        while (changed) {
                            changed = false
                            refRegex.findAll(replaced).forEach { m ->
                                val name = m.groupValues[1]
                                if (name in visited) return@forEach
                                val r = resolveVariable(project, name, scope, visited + varName)
                                if (r != null) {
                                    replaced = replaced.replace(m.value, r)
                                    changed = true
                                }
                            }
                        }

                        val evaluated: String = ArithmeticEvaluator.evaluate(replaced) ?: replaced

                        cache[key] = evaluated
                        return evaluated
                    }
                }
            }
            return null                                      // ikke funnet → ingen cache
        } catch (e: ProcessCanceledException) {
            // CRITICAL: Always rethrow ProcessCanceledException
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving preprocessor variable: $varName", e)
            return null
        }
    }

    fun clearCache() = cache.clear()
}