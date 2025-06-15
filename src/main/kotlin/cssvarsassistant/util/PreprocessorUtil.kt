package cssvarsassistant.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.PREPROCESSOR_VARIABLE_INDEX_NAME
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Resolves LESS / SCSS variables and simple arithmetic expressions.
 *
 * Supported patterns:
 *   (@base * 0.5) (@base / 3) (@base + 2) (@base - 1)
 *   (@base ** 2)  (@base % 4)
 *   (@base min 4) (@base max 10)
 *   (@base floor) (@base ceil) (@base round)
 */
object PreprocessorUtil {

    private val LOG = Logger.getInstance(PreprocessorUtil::class.java)
    private val cache = ConcurrentHashMap<Triple<Project, String, Int>, String?>()

    /** Public entry-point */
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

            for (raw in values) {
                ProgressManager.checkCanceled()

                /* ---------- arithmetic ----------------------------------- */
                parseArithmetic(raw)?.let { (baseVar, op, rhsMaybe) ->
                    val baseVal = resolveVariable(project, baseVar, scope, visited + varName)
                    if (baseVal != null) {
                        compute(baseVal, op, rhsMaybe)?.let { computed ->
                            cache[key] = computed
                            return computed
                        }
                    }
                }

                /* ---------- reference to another variable ---------------- */
                Regex("""^[\s]*[@$]([\w-]+)""").find(raw)?.let { m ->
                    val resolved = resolveVariable(
                        project, m.groupValues[1], scope, visited + varName
                    ) ?: raw
                    cache[key] = resolved
                    return resolved
                }

                /* ---------- literal -------------------------------------- */
                cache[key] = raw
                return raw
            }

            null
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving @$varName", e)
            null
        }
    }

    /* --------------------------------------------------------------------- */
    /*  Helpers                                                              */
    /* --------------------------------------------------------------------- */

    /** Matches the full `(@foo * 0.5)` - style expression. */
    private val ARITH_RE = Regex(
        """\(\s*[@$]([\w-]+)\s*(\*\*|[*/%+\-]|min|max|floor|ceil|round)\s*([+-]?\d*\.?\d+)?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private fun parseArithmetic(raw: String): Triple<String, String, String?>? =
        ARITH_RE.find(raw)?.let { m ->
            val base = m.groupValues[1]
            val op = m.groupValues[2].lowercase()
            val rhs = m.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            Triple(base, op, rhs)
        }

    private val NUM_UNIT = Regex("""([+-]?\d*\.?\d+)([a-z%]+)""", RegexOption.IGNORE_CASE)

    /**
     * Computes `baseVal (op) rhs` and returns a formatted string with unit.
     * `rhs` may be null for unary ops.
     */
    private fun compute(baseVal: String, op: String, rhs: String?): String? {
        val m = NUM_UNIT.find(baseVal) ?: return null
        val num = m.groupValues[1].toDouble()
        val unit = m.groupValues[2]

        val rhsNum = rhs?.toDoubleOrNull()

        val resultNum = when (op) {
            "*" -> rhsNum?.let { num * it }
            "/" -> rhsNum?.let { num / it }
            "+" -> rhsNum?.let { num + it }
            "-" -> rhsNum?.let { num - it }
            "%" -> rhsNum?.let { num % it }
            "**" -> rhsNum?.let { num.pow(it) }
            "min" -> rhsNum?.let { min(num, it) }
            "max" -> rhsNum?.let { max(num, it) }
            "floor" -> floor(num)
            "ceil" -> ceil(num)
            "round" -> round(num)
            else -> null
        } ?: return null

        return format(resultNum, unit)
    }

    /** Keeps integers pretty, decimals ≤ 3 dp. */
    private fun format(n: Double, unit: String): String =
        if (n % 1 == 0.0) "${n.roundToInt()}$unit"
        else "${"%.3f".format(n).trimEnd('0').trimEnd('.')} $unit".replace(" ", "")

    fun clearCache() = cache.clear()
}
