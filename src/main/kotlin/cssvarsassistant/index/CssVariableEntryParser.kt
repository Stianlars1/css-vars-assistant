package cssvarsassistant.index

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

internal object CssVariableEntryParser {

    private const val DEFAULT_CONTEXT = "default"

    // Declaration terminator is `;` OR a lookahead for `}`. The second form
    // catches the last declaration in a minified block where the trailing
    // semicolon is omitted: `:root{--primary:#ff0000;--size:4px}` must emit
    // both variables, not just the first. Community contributor @pierreoa
    // surfaced this class of bug via PR #17 against the older pre-refactor
    // code path; the regex here is the equivalent fix in the refactored
    // parser. Value capture uses `[^;}]` so a stray `}` inside a value
    // (pathological but possible) doesn't slip through, and is non-greedy so
    // the lookahead terminator binds correctly.
    private val variableDeclarationRegex = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*([^;}]+?)\s*(?:;|(?=\}))""")
    private val variableDeclarationStartRegex = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*(.*)$""")
    private val sassVariableDeclarationRegex = Regex("""^\s*(--[A-Za-z0-9\-_]+)\s*:\s*(.+?)\s*$""")

    // Single-line `/* ... */` comments embedded inside an otherwise normal
    // line. Multi-line block comments are handled separately via the
    // `inBlockComment` state machine below.
    private val inlineBlockCommentRegex = Regex("""/\*.*?\*/""")

    // SCSS/Less `// comment` to end of line. CSS itself doesn't support
    // `//` comments but the Jetbrains CSS plugin treats them as such inside
    // SCSS/Less files, so we strip them here too.
    private val lineCommentRegex = Regex("""//[^\n]*$""")

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

    fun parse(text: CharSequence, extension: String? = null): List<ParsedCssVariableEntry> {
        val entries = mutableListOf<ParsedCssVariableEntry>()
        val blockContexts = ArrayDeque<BlockContext>()
        var braceDepth = 0
        var pendingBlockContextAlternatives: List<String>? = null

        var lastComment: String? = null
        var inBlockComment = false
        val blockComment = StringBuilder()
        var pendingVariableName: String? = null
        var pendingVariableContexts: List<String>? = null
        var pendingVariableComment: String? = null
        var pendingVariableLine: Int = -1
        val pendingVariableValue = StringBuilder()

        for ((lineIndex, rawLine) in text.lines().withIndex()) {
            val currentLineNumber = lineIndex + 1  // 1-based
            var line = rawLine.trim()

            // 1) Multi-line block comment continuation (opened on an earlier line)
            if (inBlockComment) {
                val closeIdx = line.indexOf("*/")
                if (closeIdx >= 0) {
                    blockComment.append("\n" + line.substring(0, closeIdx))
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                    line = line.substring(closeIdx + 2).trim()
                    // fall through — the remainder of the line may be a
                    // legit declaration (Issue #18 F6).
                } else {
                    blockComment.append("\n" + line)
                    continue
                }
            }

            // 2) Peel any number of leading `/* ... */` block comments from
            //    the line. The LAST one wins as `lastComment` because it's
            //    the one nearest the upcoming declaration. An unclosed
            //    `/* ...` flips into multi-line mode and we break.
            while (line.startsWith("/*")) {
                val closeIdx = line.indexOf("*/")
                if (closeIdx < 0) {
                    inBlockComment = true
                    blockComment.clear()
                    blockComment.append(
                        line.removePrefix("/**").removePrefix("/*").trim()
                    )
                    line = ""
                    break
                }
                blockComment.clear()
                blockComment.append(
                    line.substring(0, closeIdx)
                        .removePrefix("/**").removePrefix("/*")
                        .trim()
                )
                lastComment = blockComment.toString().trim()
                line = line.substring(closeIdx + 2).trim()
            }
            if (inBlockComment || line.isEmpty()) continue

            // 3) Strip any remaining embedded comments from the line so they
            //    don't leak into captured values (Issue #18 F7).
            //    `--x: /* inline */ 1;` must yield value = "1", not
            //    "/* inline */ 1".
            line = inlineBlockCommentRegex.replace(line, " ")
            line = lineCommentRegex.replace(line, "").trim()
            if (line.isEmpty()) continue

            val mediaContext = extractMediaContext(line)
            if (mediaContext != null) {
                pendingBlockContextAlternatives = listOf(mediaContext)
            } else {
                val selectorContexts = extractSelectorContexts(line)
                if (selectorContexts != null) {
                    pendingBlockContextAlternatives = selectorContexts
                }
            }

            val openingBraces = line.count { it == '{' }
            val closingBraces = line.count { it == '}' }

            if (pendingBlockContextAlternatives != null && openingBraces > 0) {
                blockContexts.addLast(
                    BlockContext(
                        alternatives = requireNotNull(pendingBlockContextAlternatives),
                        depthAfterOpen = braceDepth + 1
                    )
                )
                pendingBlockContextAlternatives = null
            }

            val currentContexts = buildCurrentContexts(blockContexts)
            if (pendingVariableName != null) {
                val endIndex = line.indexOf(';')
                if (endIndex >= 0) {
                    if (pendingVariableValue.isNotEmpty()) {
                        pendingVariableValue.append('\n')
                    }
                    pendingVariableValue.append(line.substring(0, endIndex).trim())
                    val pendingName = requireNotNull(pendingVariableName)
                    val pendingContexts = requireNotNull(pendingVariableContexts)
                    val pendingValue = normalizeValue(pendingVariableValue.toString())
                    val pendingCommentText = pendingVariableComment.orEmpty()
                    for (ctx in pendingContexts) {
                        entries += ParsedCssVariableEntry(
                            name = pendingName,
                            context = ctx,
                            value = pendingValue,
                            comment = pendingCommentText,
                            // Multi-line value: the line number is the one
                            // where `--name:` opened, not where `;` closed
                            // the value.
                            line = pendingVariableLine
                        )
                    }
                    pendingVariableName = null
                    pendingVariableContexts = null
                    pendingVariableComment = null
                    pendingVariableLine = -1
                    pendingVariableValue.clear()
                    lastComment = null
                } else {
                    if (pendingVariableValue.isNotEmpty()) {
                        pendingVariableValue.append('\n')
                    }
                    pendingVariableValue.append(line)
                }
            } else {
                val matches = variableDeclarationRegex.findAll(line).toList()

                matches.forEachIndexed { index, match ->
                    val emittedName = match.groupValues[1]
                    val emittedValue = match.groupValues[2].trim()
                    val emittedComment = if (index == 0) lastComment ?: "" else ""
                    for (ctx in currentContexts) {
                        entries += ParsedCssVariableEntry(
                            name = emittedName,
                            context = ctx,
                            value = emittedValue,
                            comment = emittedComment,
                            line = currentLineNumber
                        )
                    }
                }

                if (matches.isNotEmpty()) {
                    lastComment = null
                } else {
                    val multilineMatch = variableDeclarationStartRegex.find(line)
                    if (multilineMatch != null && ';' !in multilineMatch.groupValues[2]) {
                        pendingVariableName = multilineMatch.groupValues[1]
                        pendingVariableContexts = currentContexts
                        pendingVariableComment = lastComment
                        pendingVariableLine = currentLineNumber
                        pendingVariableValue.clear()
                        pendingVariableValue.append(multilineMatch.groupValues[2].trim())
                    }
                }
            }

            braceDepth = (braceDepth + openingBraces - closingBraces).coerceAtLeast(0)
            while (blockContexts.isNotEmpty() && blockContexts.last().depthAfterOpen > braceDepth) {
                blockContexts.removeLast()
            }
        }

        if (extension?.lowercase() == "sass") {
            val seen = entries.asSequence()
                .map { it.name to it.line }
                .toSet()
            entries += parseSassLineCustomProperties(text)
                .filterNot { it.name to it.line in seen }
        }

        return entries.sortedBy { it.line }
    }

    private fun parseSassLineCustomProperties(text: CharSequence): List<ParsedCssVariableEntry> {
        val entries = mutableListOf<ParsedCssVariableEntry>()
        var inBlockComment = false

        for ((lineIndex, rawLine) in text.lines().withIndex()) {
            var line = rawLine

            if (inBlockComment) {
                val closeIdx = line.indexOf("*/")
                if (closeIdx < 0) continue
                inBlockComment = false
                line = line.substring(closeIdx + 2)
            }

            while (true) {
                val openIdx = line.indexOf("/*")
                if (openIdx < 0) break
                val closeIdx = line.indexOf("*/", startIndex = openIdx + 2)
                if (closeIdx < 0) {
                    line = line.substring(0, openIdx)
                    inBlockComment = true
                    break
                }
                line = line.removeRange(openIdx, closeIdx + 2)
            }

            line = lineCommentRegex.replace(line, "").trim()
            if (line.isEmpty()) continue

            val match = sassVariableDeclarationRegex.find(line) ?: continue
            val value = match.groupValues[2].trim()
            if (value.endsWith(";")) continue

            entries += ParsedCssVariableEntry(
                name = match.groupValues[1],
                context = DEFAULT_CONTEXT,
                value = normalizeValue(value),
                comment = "",
                line = lineIndex + 1
            )
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

    private fun normalizeValue(value: String): String =
        value
            .lineSequence()
            .joinToString(" ") { it.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()
}
