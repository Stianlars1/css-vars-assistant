package cssvarsassistant.index

import cssvarsassistant.util.CssTextUtil
import cssvarsassistant.util.StylesheetStatements

internal data class VariableScope(val start: Int, var end: Int, val parent: Int?)

internal data class PreprocessorDeclaration(
    val name: String,
    val value: String,
    val offset: Int,
    val line: Int,
    val scope: List<Int>,
    val defaultOnly: Boolean
)

internal data class PreprocessorFileData(
    val declarations: List<PreprocessorDeclaration>,
    val scopes: List<VariableScope>
) {
    private val byName = declarations.groupBy { canonicalPreprocessorName(it.name) }
    fun named(name: String): List<PreprocessorDeclaration> = byName[canonicalPreprocessorName(name)].orEmpty()

    fun scopeAt(offset: Int): List<Int> = scopes.filter { offset in it.start until it.end }.map { it.start }

    fun visible(name: String, offset: Int, less: Boolean): PreprocessorDeclaration? {
        val path = scopeAt(offset)
        val candidates = named(name).filter {
                (less || it.offset < offset) && path.take(it.scope.size) == it.scope
        }
        for (level in path.size downTo 0) {
            var selected: PreprocessorDeclaration? = null
            for (declaration in candidates.filter { it.scope.size == level }) {
                if (!declaration.defaultOnly || selected == null || selected.value == "null") selected = declaration
            }
            if (selected != null) return selected
        }
        return null
    }
}

internal fun canonicalPreprocessorName(name: String): String =
    if (name.contains('$')) name.replace('_', '-') else name

internal object PreprocessorVariableEntryParser {
    private val declaration = Regex("""^([$@][\p{L}\p{N}_-]+)\s*:\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
    private val flag = Regex("""\s+!(default|global)\s*$""")
    private val property = Regex("""^[\p{L}\p{N}_-]+\s*:""")

    fun parse(text: CharSequence, extension: String?): Map<String, String> {
        val data = declarations(text.toString(), extension)
        return data.declarations.groupBy { canonicalPreprocessorName(it.name) }.mapValues { (_, entries) ->
            val globals = entries.filter { it.scope.isEmpty() }
            var selected: PreprocessorDeclaration? = null
            for (entry in globals.ifEmpty { entries }) {
                if (!entry.defaultOnly || selected == null || selected.value == "null") selected = entry
            }
            requireNotNull(selected).value
        }
    }

    fun declarations(text: String, extension: String?): PreprocessorFileData {
        if (extension !in setOf("scss", "sass", "less")) return PreprocessorFileData(emptyList(), emptyList())
        val entries = mutableListOf<PreprocessorDeclaration>()
        val scopes = mutableListOf<VariableScope>()
        val stack = ArrayDeque<Pair<VariableScope, Int>>()
        val sass = extension == "sass"
        for (statement in StylesheetStatements.parse(text, extension)) {
            if (sass) {
                while (stack.isNotEmpty() && stack.last().second >= statement.indent) stack.removeLast().first.end = statement.offset
            }
            val match = declaration.matchEntire(statement.text)
            if (match != null && (statement.terminator != '\u0000' || sass)) {
                val name = match.groupValues[1]
                if ((name.startsWith('@') && extension == "less") || (name.startsWith('$') && extension != "less")) {
                    var value = match.groupValues[2].trim()
                    val flags = mutableSetOf<String>()
                    if (extension != "less") {
                        while (true) {
                            val suffix = flag.find(value) ?: break
                            flags += suffix.groupValues[1]
                            value = value.substring(0, suffix.range.first).trimEnd()
                        }
                    }
                    if (value.isNotEmpty()) entries += PreprocessorDeclaration(
                        name, CssTextUtil.normalizeWhitespace(value), statement.offset, statement.line,
                        if ("global" in flags) emptyList() else stack.map { it.first.start }, "default" in flags
                    )
                }
            }
            val sassBlock = sass && match == null && statement.text.isNotBlank() &&
                !property.containsMatchIn(statement.text) && !Regex("""^@(use|forward|import)\b""").containsMatchIn(statement.text)
            if (statement.terminator == '{' || sassBlock) {
                val scope = VariableScope(statement.offset, text.length, stack.lastOrNull()?.first?.start)
                scopes += scope
                stack.addLast(scope to statement.indent)
            }
            if (statement.terminator == '}' && stack.isNotEmpty()) stack.removeLast().first.end = statement.offset + statement.text.length
        }
        return PreprocessorFileData(entries, scopes)
    }
}
