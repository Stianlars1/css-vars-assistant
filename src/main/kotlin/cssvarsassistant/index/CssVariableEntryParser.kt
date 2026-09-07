package cssvarsassistant.index

import cssvarsassistant.util.CssTextUtil
import cssvarsassistant.util.StylesheetStatements

internal data class ParsedCssVariableEntry(
    val name: String,
    val context: String,
    val value: String,
    val comment: String,
    // 1-based line number of the source where the declaration opens (for a
    // multi-line value, this is the line containing `--name:`, not where the
    // closing `;` lives). `-1` means "unknown" — used only as a fallback when
    // decoding a legacy 3-part record from an older index cache.
    val line: Int
)

internal data class LocatedCssVariable(val entry: ParsedCssVariableEntry, val offset: Int)

internal object CssVariableEntryParser {

    private const val DEFAULT_CONTEXT = "default"

    private val declaration = Regex("""^(--[\p{L}\p{N}_-]+)\s*:\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)

    // Selectors that effectively represent the document root in a design-token
    // context. Declarations inside these do NOT push a new context — they fall
    // through as "default" because they're the baseline every theme overrides.
    private val ROOT_LIKE_SELECTORS = setOf(":root", ":host", "html", "body", "*")

    // Max visible length of a selector label in the popup Context column.
    // Long comma-separated selector lists get truncated with an ellipsis.
    private const val MAX_SELECTOR_LABEL = 60

    // Issue #29 — attribute-equals selector canonicalisation. `[a="b"]`,
    // `[a='b']`, and `[a=b]` are semantically identical in CSS but were
    // stored as three distinct context strings, causing duplicate merged
    // labels in the hover popup. Strip the quotes at parse time so the
    // index sees one canonical shape per value.
    private val attributeEqualsRegex =
        Regex("""\[\s*((?:[\w-]+\|)?[\w-]+)\s*([~|^$*]?=)\s*['"]?([\w-]+)['"]?\s*]""")

    // Generalised from the 1.7.x `MediaContext`: any block that contributes a
    // label to the current context stack. Media queries push `(min-width: ...)`,
    // non-root selectors push `.dark`, `[data-theme="dark"]`, etc. Nested pushes
    // combine labels by space.
    private data class BlockContext(
        val alternatives: List<String>,
        val depthAfterOpen: Int
    )

    fun parse(text: CharSequence, extension: String? = null): List<ParsedCssVariableEntry> =
        declarations(text, extension).map { it.entry }

    fun declarations(text: CharSequence, extension: String? = null): List<LocatedCssVariable> {
        val entries = mutableListOf<LocatedCssVariable>()
        val contexts = ArrayDeque<BlockContext>()
        val sass = extension?.lowercase() == "sass"
        var depth = 0
        var pendingComment = ""
        for (statement in StylesheetStatements.parse(text.toString(), extension?.lowercase())) {
            if (sass) {
                while (contexts.isNotEmpty() && contexts.last().depthAfterOpen >= statement.indent) contexts.removeLast()
            }
            if (statement.comment.isNotEmpty()) pendingComment = statement.comment
            val match = declaration.matchEntire(statement.text)
            if (match != null && (statement.terminator != '\u0000' || sass)) {
                val value = CssTextUtil.normalizeWhitespace(match.groupValues[2])
                if (value.isNotEmpty()) {
                    for (context in buildCurrentContexts(contexts)) {
                        entries += LocatedCssVariable(ParsedCssVariableEntry(match.groupValues[1], context, value, pendingComment, statement.line), statement.offset)
                    }
                }
                pendingComment = ""
            }
            if (statement.terminator == '{' || (sass && match == null && statement.text.isNotBlank())) {
                depth++
                val header = statement.text + " {"
                val alternatives = extractMediaContext(header)?.let(::listOf)
                    ?: extractSelectorContexts(header)
                if (alternatives != null) contexts.addLast(BlockContext(alternatives, if (sass) statement.indent else depth))
            }
            if (statement.terminator == '}') {
                depth = (depth - 1).coerceAtLeast(0)
                while (contexts.isNotEmpty() && contexts.last().depthAfterOpen > depth) contexts.removeLast()
            }
        }
        return entries
    }

    internal fun extractMediaContext(line: String): String? {
        if (!line.startsWith("@media", ignoreCase = true)) {
            return null
        }

        return line
            .replaceFirst(Regex("""@media\b""", RegexOption.IGNORE_CASE), "")
            .substringBefore("{")
            .trim()
            .ifEmpty { "media" }
    }

    private fun buildCurrentContexts(blockContexts: ArrayDeque<BlockContext>): List<String> {
        var contexts = listOf("")
        for (blockContext in blockContexts) {
            contexts = contexts.flatMap { prefix ->
                blockContext.alternatives.map { alternative ->
                    listOf(prefix, alternative)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            }
        }
        return contexts
            .map { it.ifBlank { DEFAULT_CONTEXT } }
            .distinct()
    }

    // Phase 8a / issue #19: detect non-root selector blocks as contexts so
    // `[data-theme="dark"] { --bg: black }` shows up as its own row in the
    // hover popup instead of silently overwriting the default value.
    //
    // Issue #29: comma-separated selector lists get canonicalised — root-like
    // sub-selectors are lifted out (they mean "default context"), attribute
    // quoting is normalised, and duplicates are removed. Depending on the
    // shape of the resulting list this function returns one of:
    //
    //   - `null` → all elements are root-like; declarations fall through into
    //              the ambient context (or `default` at top level).
    //   - `listOf("<selector-list>")` → no root-like element; preserve the
    //                                  canonicalised selector-list context.
    //   - `listOf("", "<theme>", ...)` → the list contains root-like and
    //                                    non-root elements. The empty
    //                                    alternative preserves the ambient
    //                                    context while each theme alternative
    //                                    adds its selector context.
    private fun extractSelectorContexts(line: String): List<String>? {
        val openIdx = line.indexOf('{')
        if (openIdx < 0) return null
        val prefix = line.substring(0, openIdx).trim()
        if (prefix.isEmpty()) return null
        // `@media`, `@supports`, `@container`, etc. — at-rules are handled
        // (or intentionally skipped) elsewhere, never as selector labels.
        if (prefix.startsWith("@")) return null

        val parts = splitSelectorList(prefix)
        // Single-selector, root-like → default context (unchanged behaviour).
        if (parts.size == 1 && parts.single().lowercase() in ROOT_LIKE_SELECTORS) {
            return null
        }

        val canonical = parts
            .map(::canonicaliseSelectorPart)
            .distinct()

        val (rootLike, themeLike) = canonical.partition { it.lowercase() in ROOT_LIKE_SELECTORS }
        if (themeLike.isEmpty()) {
            // Every element was root-like; the whole list collapses to default.
            return null
        }

        return if (rootLike.isEmpty()) {
            listOf(truncateSelectorLabel(themeLike.joinToString(", ")))
        } else {
            listOf("") + themeLike.map(::truncateSelectorLabel)
        }
    }

    // Split a top-level comma list, ignoring commas that live inside `[...]`,
    // `(...)`, or `"..."` / `'...'` — those aren't list separators. This
    // keeps `[data-theme="a,b"]` and `:is(.a, .b)` intact as single entries.
    private fun splitSelectorList(prefix: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false
        for (ch in prefix) {
            when {
                inSingleQuote -> {
                    current.append(ch)
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '\'' -> inSingleQuote = false
                    }
                }
                inDoubleQuote -> {
                    current.append(ch)
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inDoubleQuote = false
                    }
                }
                ch == '\'' -> { current.append(ch); inSingleQuote = true }
                ch == '"' -> { current.append(ch); inDoubleQuote = true }
                ch == '[' || ch == '(' -> { current.append(ch); depth++ }
                ch == ']' || ch == ')' -> { current.append(ch); depth = (depth - 1).coerceAtLeast(0) }
                ch == ',' && depth == 0 -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) parts += tail
        return parts.filter { it.isNotEmpty() }
    }

    // Normalise an individual selector part so semantically-equivalent shapes
    // land as one canonical string. Currently only strips redundant quoting
    // from attribute-equals selectors — the most common source of duplicate
    // rows in themed design systems.
    private fun canonicaliseSelectorPart(part: String): String {
        val collapsed = part.replace(Regex("""\s+"""), " ").trim()
        return attributeEqualsRegex.replace(collapsed) { match ->
            "[${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}]"
        }
    }

    private fun truncateSelectorLabel(label: String): String =
        if (label.length <= MAX_SELECTOR_LABEL) label
        else label.take(MAX_SELECTOR_LABEL - 1).trimEnd().trimEnd(',') + "…"

}
