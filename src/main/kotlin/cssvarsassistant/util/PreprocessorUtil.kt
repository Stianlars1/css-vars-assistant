package cssvarsassistant.util

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.documentation.ResolutionInfo
import cssvarsassistant.documentation.VariableResolver
import kotlin.math.*

/** Compatibility entry points; resolution caches are confined to one request. */
object PreprocessorUtil {
    private val preprocessorReferenceRegex = Regex("""^(?:[\p{L}\p{N}_-]+\.)?[$@][\p{L}\p{N}_-]+$""")
    private val arithmeticRegex = Regex("""\(\s*([@$])([\p{L}\p{N}_-]+)\s*(\*\*|[*/%+\-]|min|max|floor|ceil|round)\s*([+-]?\d*\.?\d+)?\s*\)""", RegexOption.IGNORE_CASE)
    private val numericUnitRegex = Regex("""([+-]?(?:\d+(?:\.\d*)?|\.\d+))([a-z%]*)""", RegexOption.IGNORE_CASE)
    internal data class ArithmeticExpression(val reference: String, val operator: String, val rhs: String?)

    fun resolveVariable(project: Project, varName: String, scope: GlobalSearchScope, visited: Set<String> = emptySet()): String? =
        resolveVariableWithSteps(project, varName, scope, visited).resolved

    fun resolveVariableWithSteps(project: Project, varName: String, scope: GlobalSearchScope, visited: Set<String> = emptySet(), steps: List<String> = emptyList()): ResolutionInfo =
        VariableResolver(project, scope).resolve(varName, visited = visited, steps = steps, expandCss = false)

    fun isPreprocessorReference(value: String): Boolean = preprocessorReferenceRegex.matches(value.trim())

    internal fun parseArithmetic(raw: String): ArithmeticExpression? = arithmeticRegex.matchEntire(raw)?.let {
        ArithmeticExpression(it.groupValues[1] + it.groupValues[2], it.groupValues[3].lowercase(), it.groupValues[4].takeIf(String::isNotBlank))
    }

    internal fun compute(baseValue: String, op: String, rhs: String?): String? {
        val match = numericUnitRegex.matchEntire(baseValue.trim()) ?: return null
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
        "${java.math.BigDecimal.valueOf(number).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}$unit"

}
