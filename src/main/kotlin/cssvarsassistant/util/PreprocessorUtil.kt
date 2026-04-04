package cssvarsassistant.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.documentation.ResolutionInfo
import cssvarsassistant.index.PREPROCESSOR_VARIABLE_INDEX_NAME
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

private data class PreprocessorCacheKey(
    val variableName: String,
    val scopeHash: Int
)

internal data class CachedPreprocessorResolution(
    val resolved: String,
    val canonicalSteps: List<String>
) {
    fun toResolutionInfo(variableName: String, prefixSteps: List<String>): ResolutionInfo =
        ResolutionInfo(
            original = "@$variableName",
            resolved = resolved,
            steps = prefixSteps + canonicalSteps
        )
}

@Service(Service.Level.PROJECT)
internal class PreprocessorResolutionCache : Disposable {
    private val values = java.util.concurrent.ConcurrentHashMap<PreprocessorCacheKey, CachedPreprocessorResolution>()

    fun get(variableName: String, scope: GlobalSearchScope): CachedPreprocessorResolution? =
        values[PreprocessorCacheKey(variableName, scope.hashCode())]

    fun put(variableName: String, scope: GlobalSearchScope, resolution: CachedPreprocessorResolution) {
        values[PreprocessorCacheKey(variableName, scope.hashCode())] = resolution
    }

    fun clear() = values.clear()

    override fun dispose() = clear()
}

/**
 * Resolves LESS / SCSS variables and simple arithmetic expressions.
 *
 * The cache is project-scoped and stores canonical resolution chains that start
 * at the preprocessor variable itself, so caller-specific prefixes never leak
 * across completion, documentation, or hint generation.
 */
object PreprocessorUtil {

    private val LOG = Logger.getInstance(PreprocessorUtil::class.java)
    private val preprocessorReferenceRegex = Regex("""^[\s]*[@$]([\w-]+)""")

    /** Matches the full `(@foo * 0.5)` - style expression. */
    private val arithmeticRegex = Regex(
        """\(\s*[@$]([\w-]+)\s*(\*\*|[*/%+\-]|min|max|floor|ceil|round)\s*([+-]?\d*\.?\d+)?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    private val numericUnitRegex = Regex("""([+-]?\d*\.?\d+)([a-z%]+)""", RegexOption.IGNORE_CASE)

    fun resolveVariable(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet()
    ): String? {
        return resolveVariableWithSteps(project, varName, scope, visited).resolved
    }

    fun resolveVariableWithSteps(
        project: Project,
        varName: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet(),
        steps: List<String> = emptyList()
    ): ResolutionInfo {
        ProgressManager.checkCanceled()

        val normalizedName = varName.removePrefix("@").removePrefix("$")
        if (normalizedName in visited) {
            return ResolutionInfo(normalizedName, normalizedName, steps)
        }

        cache(project).get(normalizedName, scope)?.let { cachedResolution ->
            return cachedResolution.toResolutionInfo(normalizedName, steps)
        }

        return try {
            val values = FileBasedIndex.getInstance()
                .getValues(PREPROCESSOR_VARIABLE_INDEX_NAME, normalizedName, scope)

            if (values.isEmpty()) {
                return ResolutionInfo(normalizedName, normalizedName, steps)
            }

            for (raw in values) {
                ProgressManager.checkCanceled()

                parseArithmetic(raw)?.let { (baseVar, op, rhsMaybe) ->
                    val baseResolution = resolveVariableWithSteps(
                        project,
                        baseVar,
                        scope,
                        visited + normalizedName,
                        steps + "@$normalizedName"
                    )
                    val baseValue = baseResolution.resolved

                    compute(baseValue, op, rhsMaybe)?.let { computed ->
                        val result = ResolutionInfo(
                            original = "@$normalizedName",
                            resolved = computed,
                            steps = baseResolution.steps + "($baseValue $op ${rhsMaybe ?: ""}) = $computed"
                        )
                        putCached(project, normalizedName, scope, result)
                        return result
                    }
                }

                preprocessorReferenceRegex.find(raw)?.let { match ->
                    val referenceName = match.groupValues[1]
                    val resolution = resolveVariableWithSteps(
                        project,
                        referenceName,
                        scope,
                        visited + normalizedName,
                        steps + "@$normalizedName"
                    )
                    val result = ResolutionInfo(
                        original = "@$normalizedName",
                        resolved = resolution.resolved,
                        steps = resolution.steps
                    )
                    putCached(project, normalizedName, scope, result)
                    return result
                }

                val result = ResolutionInfo(
                    original = "@$normalizedName",
                    resolved = raw,
                    steps = steps + "@$normalizedName"
                )
                putCached(project, normalizedName, scope, result)
                return result
            }

            ResolutionInfo(normalizedName, normalizedName, steps)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving @$normalizedName", e)
            ResolutionInfo(normalizedName, normalizedName, steps)
        }
    }

    fun isPreprocessorReference(value: String): Boolean =
        preprocessorReferenceRegex.matches(value.trim())

    fun clearCache(project: Project) {
        cache(project).clear()
    }

    fun clearCache() {
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                cache(project).clear()
            }
        }
    }

    private fun parseArithmetic(raw: String): Triple<String, String, String?>? =
        arithmeticRegex.find(raw)?.let { match ->
            val base = match.groupValues[1]
            val op = match.groupValues[2].lowercase()
            val rhs = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }
            Triple(base, op, rhs)
        }

    private fun compute(baseValue: String, op: String, rhs: String?): String? {
        val match = numericUnitRegex.find(baseValue) ?: return null
        val number = match.groupValues[1].toDouble()
        val unit = match.groupValues[2]
        val rhsNumber = rhs?.toDoubleOrNull()

        val resultNumber = when (op) {
            "*" -> rhsNumber?.let { number * it }
            "/" -> rhsNumber?.let { number / it }
            "+" -> rhsNumber?.let { number + it }
            "-" -> rhsNumber?.let { number - it }
            "%" -> rhsNumber?.let { number % it }
            "**" -> rhsNumber?.let { number.pow(it) }
            "min" -> rhsNumber?.let { min(number, it) }
            "max" -> rhsNumber?.let { max(number, it) }
            "floor" -> floor(number)
            "ceil" -> ceil(number)
            "round" -> round(number)
            else -> null
        } ?: return null

        if (resultNumber.isNaN() || resultNumber.isInfinite()) {
            return null
        }

        return format(resultNumber, unit)
    }

    private fun format(number: Double, unit: String): String =
        if (number % 1 == 0.0) "${number.roundToInt()}$unit"
        else "${"%.3f".format(number).trimEnd('0').trimEnd('.')}$unit"

    private fun putCached(
        project: Project,
        variableName: String,
        scope: GlobalSearchScope,
        resolution: ResolutionInfo
    ) {
        cache(project).put(
            variableName,
            scope,
            CachedPreprocessorResolution(
                resolved = resolution.resolved,
                canonicalSteps = canonicalizeSteps(variableName, resolution.steps)
            )
        )
    }

    private fun canonicalizeSteps(variableName: String, steps: List<String>): List<String> {
        val canonicalStart = steps.indexOfFirst { it == "@$variableName" || it == "\$$variableName" }
        return if (canonicalStart >= 0) steps.drop(canonicalStart) else steps
    }

    private fun cache(project: Project): PreprocessorResolutionCache =
        project.getService(PreprocessorResolutionCache::class.java)
}
