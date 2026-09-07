package cssvarsassistant.documentation

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.index.ImportResolver
import cssvarsassistant.index.PreprocessorLookup
import cssvarsassistant.index.SourcedCssValue
import cssvarsassistant.index.VariableLocation
import cssvarsassistant.index.VariableLookup
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.CssTextUtil
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

/** One request owns lookup reuse; every expression has its own bounded recursion path. */
internal class VariableResolver(
    private val project: Project,
    preprocessorScope: GlobalSearchScope = ScopeUtil.currentPreprocessorScope(project)
) {
    private data class Value(val text: String, val steps: List<String> = emptyList(), val cyclic: Boolean = false)
    private val settings = CssVarsAssistantSettings.getInstance()
    private val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)
    private val preprocessor = PreprocessorLookup(project, preprocessorScope)
    private val cssValues = mutableMapOf<String, List<SourcedCssValue>>()
    private val imports = mutableMapOf<VirtualFile, List<VirtualFile>>()
    private var operations = 0

    fun resolve(
        raw: String,
        location: VariableLocation? = null,
        visited: Set<String> = emptySet(),
        depth: Int = 0,
        steps: List<String> = emptyList(),
        expandCss: Boolean = true
    ): ResolutionInfo {
        operations = 0
        val value = evaluate(raw, location, visited, depth, expandCss)
        return if (value.cyclic) ResolutionInfo(raw, raw, steps) else ResolutionInfo(raw, value.text, steps + value.steps)
    }

    private fun evaluate(raw: String, location: VariableLocation?, path: Set<String>, depth: Int, expandCss: Boolean): Value {
        ProgressManager.checkCanceled()
        if (depth > settings.maxImportDepth || ++operations > MAX_OPERATIONS || raw.length > MAX_VALUE_LENGTH) return Value(raw)
        val trimmed = raw.trim()
        if (PREPROCESSOR.matches(trimmed)) {
            val declaration = preprocessor.find(trimmed, location) ?: return Value(raw)
            val entry = declaration.declaration
            val key = "pp:${declaration.file.url}:${entry.offset}:${entry.name}"
            if (key in path || trimmed in path) return Value(raw, cyclic = true)
            val expandAlias = expandCss && (extractCssVarAlias(entry.value) != null || PREPROCESSOR.matches(entry.value.trim()))
            val nested = evaluate(entry.value, VariableLocation(declaration.file, entry.offset, cssContext = location?.cssContext), path + key, depth + 1, expandAlias)
            return Value(nested.text, listOf(entry.name) + nested.steps, nested.cyclic)
        }
        PreprocessorUtil.parseArithmetic(trimmed)?.let { expression ->
            val base = evaluate(expression.reference, location, path, depth + 1, expandCss)
            PreprocessorUtil.compute(base.text, expression.operator, expression.rhs)?.let { value ->
                return Value(value, base.steps + "(${base.text} ${expression.operator} ${expression.rhs ?: ""}) = $value")
            }
        }
        if (!expandCss) return Value(raw)

        val masked = CssTextUtil.maskComments(raw, maskStrings = true).text
        val result = StringBuilder()
        val steps = mutableListOf<String>()
        var cyclic = false
        var cursor = 0
        while (cursor < raw.length) {
            ProgressManager.checkCanceled()
            if (++operations > MAX_OPERATIONS) return Value(raw)
            val match = VAR_OPEN.find(masked, cursor) ?: break
            val open = match.range.last
            var nesting = 1
            var end = open + 1
            var comma = -1
            while (end < masked.length && nesting > 0) {
                when (masked[end]) {
                    '(' -> nesting++
                    ')' -> nesting--
                    ',' -> if (nesting == 1 && comma < 0) comma = end
                }
                end++
            }
            if (nesting != 0) break
            result.append(raw, cursor, match.range.first)
            val nameEnd = if (comma >= 0) comma else end - 1
            val name = raw.substring(open + 1, nameEnd).trim()
            val original = raw.substring(match.range.first, end)
            val entries = if (CSS_NAME.matches(name)) cssValues.getOrPut(name) { VariableLookup.cssValues(project, name, cssScope) } else emptyList()
            val selected = select(entries, location)
            val key = "css:$name:${location?.cssContext.orEmpty()}"
            val replacement = when {
                key in path || name in path -> Value(original, cyclic = true)
                selected != null -> {
                    val value = selected.value
                    val nested = evaluate(value.value, VariableLocation(selected.file, value.offset.takeIf { it >= 0 } ?: Int.MAX_VALUE, cssContext = location?.cssContext ?: value.context), path + key, depth + 1, true)
                    Value(nested.text, listOf("var($name)") + nested.steps, nested.cyclic)
                }
                entries.isEmpty() && CSS_NAME.matches(name) && comma >= 0 ->
                    evaluate(raw.substring(comma + 1, end - 1).trim(), location, path, depth + 1, true)
                else -> Value(original)
            }
            result.append(replacement.text)
            steps += replacement.steps
            cyclic = cyclic || replacement.cyclic
            if (result.length > MAX_VALUE_LENGTH) return Value(raw)
            cursor = end
        }
        result.append(raw, cursor, raw.length)
        return Value(result.toString(), steps, cyclic)
    }

    private fun select(entries: List<SourcedCssValue>, location: VariableLocation?): SourcedCssValue? {
        if (entries.isEmpty()) return null
        val contextual = location?.cssContext?.let { context -> entries.filter { it.value.context == context } }.orEmpty()
        val defaults = entries.filter { it.value.context == "default" }
        val candidates = contextual.ifEmpty { defaults }.ifEmpty {
            if (entries.map { it.value.value }.distinct().size > 1) return null
            entries
        }
        val file = location?.file
        if (file == null) {
            if (candidates.map { it.file }.distinct().size > 1 && candidates.map { it.value.value }.distinct().size > 1) return null
            return candidates.maxByOrNull { it.value.offset.takeIf { offset -> offset >= 0 } ?: it.value.line }
        }
        val order = imports.getOrPut(file) { ImportResolver.resolveImports(file, project, settings.maxImportDepth).toList() + file }
        return candidates.maxWithOrNull(compareBy<SourcedCssValue> { order.indexOf(it.file) }.thenBy { it.value.line }.thenBy { it.value.offset })
    }

    companion object {
        private const val MAX_OPERATIONS = 2_000
        private const val MAX_VALUE_LENGTH = 1_048_576
        private val PREPROCESSOR = Regex("""^(?:[\p{L}\p{N}_-]+\.)?[$@][\p{L}\p{N}_-]+$""")
        private val CSS_NAME = Regex("""--[\p{L}\p{N}_-]+""")
        private val VAR_OPEN = Regex("""(?<![\p{L}\p{N}_-])var\s*\(""", RegexOption.IGNORE_CASE)
    }
}
