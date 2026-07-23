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

    // Internal separator between a root-like sub-selector and the accompanying
    // theme sub-selector when a single declaration lives inside a selector list
    // that contains BOTH shapes (e.g. `:root, [data-theme=light]`). The parser
    // splits such declarations into two `ParsedCssVariableEntry` records with
    // this token as the interior representation — the emission loop unpacks it
    // back into a `default` entry plus a theme entry with the same value, so
    // downstream `distinctBy` / collapse-by-value merges them into a single
    // `Default/<Theme>` row in the popup. The token is `U+0001` so it can
    // never appear inside a real CSS selector.
    private const val ROOT_THEME_SEPARATOR = "\u0001"

    // Issue #29 — attribute-equals selector canonicalisation. `[a="b"]`,
    // `[a='b']`, and `[a=b]` are semantically identical in CSS but were
    // stored as three distinct context strings, causing duplicate merged
    // labels in the hover popup. Strip the quotes at parse time so the
    // index sees one canonical shape per value.
    private val attributeEqualsRegex =
        Regex("""\[\s*([\w-]+)(?:\|[\w-]+)?\s*([~|^$*]?=)\s*['"]?([\w-]+)['"]?\s*]""")

    // Generalised from the 1.7.x `MediaContext`: any block that contributes a
    // label to the current context stack. Media queries push `(min-width: ...)`,
    // non-root selectors push `.dark`, `[data-theme="dark"]`, etc. Nested pushes
    // combine labels by space.
    private data class BlockContext(
        val label: String,
        val depthAfterOpen: Int
    )

    fun parse(text: CharSequence, extension: String? = null): List<ParsedCssVariableEntry> {
        val entries = mutableListOf<ParsedCssVariableEntry>()
        val blockContexts = ArrayDeque<BlockContext>()
        var braceDepth = 0
        var pendingBlockContext: String? = null

        var lastComment: String? = null
        var inBlockComment = false
        val blockComment = StringBuilder()
        var pendingVariableName: String? = null
        var pendingVariableContext: String? = null
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
                pendingBlockContext = mediaContext
            } else {
                val selectorContext = extractSelectorContext(line)
                if (selectorContext != null) {
                    pendingBlockContext = selectorContext
                }
            }

            val openingBraces = line.count { it == '{' }
            val closingBraces = line.count { it == '}' }

            if (pendingBlockContext != null && openingBraces > 0) {
                blockContexts.addLast(BlockContext(pendingBlockContext!!, braceDepth + 1))
                pendingBlockContext = null
            }

            val currentContext = if (blockContexts.isEmpty()) {
                DEFAULT_CONTEXT
            } else {
                blockContexts.joinToString(" ") { it.label }
            }
            if (pendingVariableName != null) {
                val endIndex = line.indexOf(';')
                if (endIndex >= 0) {
                    if (pendingVariableValue.isNotEmpty()) {
                        pendingVariableValue.append('\n')
                    }
                    pendingVariableValue.append(line.substring(0, endIndex).trim())
                    val pendingName = requireNotNull(pendingVariableName)
                    val pendingCtx = requireNotNull(pendingVariableContext)
                    val pendingValue = normalizeValue(pendingVariableValue.toString())
                    val pendingCommentText = pendingVariableComment.orEmpty()
                    // Issue #29: a selector-list that mixes root-like + theme
                    // selectors is stored as `default<SEP>theme` internally.
                    // Explode it back into two records with the same value so
                    // the popup's collapse-by-value merges them into a single
                    // `Default/<Theme>` row.
                    for (ctx in explodeContextForEmission(pendingCtx)) {
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
                    pendingVariableContext = null
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

                // Issue #29: root-like + theme selector-list contexts are
                // stored as `default<SEP>theme` internally. Explode into
                // one record per canonical context so downstream collapse
                // can merge them by value into `Default/<Theme>`.
                val emitContexts = explodeContextForEmission(currentContext)
                matches.forEachIndexed { index, match ->
                    val emittedName = match.groupValues[1]
                    val emittedValue = match.groupValues[2].trim()
                    val emittedComment = if (index == 0) lastComment ?: "" else ""
                    for (ctx in emitContexts) {
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
                        pendingVariableContext = currentContext
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

    // Phase 8a / issue #19: detect non-root selector blocks as contexts so
    // `[data-theme="dark"] { --bg: black }` shows up as its own row in the
    // hover popup instead of silently overwriting the default value.
    //
    // Issue #29: comma-separated selector lists get canonicalised — root-like
    // sub-selectors are lifted out (they mean "default context"), attribute
    // quoting is normalised, and duplicates are removed. Depending on the
    // shape of the resulting list this function returns one of:
    //
    //   - `null`  → all elements are root-like; declarations fall through
    //               into the ambient default context (unchanged behaviour).
    //   - "<theme-list>" → only non-root elements; the canonicalised list is
    //                      used as the block context (unchanged behaviour).
    //   - "default<SEP><theme-list>" → the list contained BOTH root-like AND
    //                                  non-root elements. The emission loop
    //                                  splits this marker back into a
    //                                  `default` entry AND a theme entry so
    //                                  the popup's collapse-by-value renders
    //                                  the merged row as "Default/<Theme>".
    internal fun extractSelectorContext(line: String): String? {
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

        val themeLabel = truncateSelectorLabel(themeLike.joinToString(", "))
        return if (rootLike.isEmpty()) {
            themeLabel
        } else {
            // Marker consumed by the emission loop below.
            DEFAULT_CONTEXT + ROOT_THEME_SEPARATOR + themeLabel
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
        for (ch in prefix) {
            when {
                inSingleQuote -> {
                    current.append(ch); if (ch == '\'') inSingleQuote = false
                }
                inDoubleQuote -> {
                    current.append(ch); if (ch == '"') inDoubleQuote = false
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

    // Split a possibly-marked context string emitted by `extractSelectorContext`
    // into the concrete list of contexts to store on the index. A plain
    // context returns a single-element list; a "default<SEP><theme>" marker
    // returns [default, theme] so the emission loop writes two records with
    // the same value.
    private fun explodeContextForEmission(context: String): List<String> {
        val sepIdx = context.indexOf(ROOT_THEME_SEPARATOR)
        if (sepIdx < 0) return listOf(context)
        val head = context.substring(0, sepIdx)
        val tail = context.substring(sepIdx + ROOT_THEME_SEPARATOR.length)
        // Only the `default+theme` shape is meaningful today; guard against
        // any future accidental use of the separator in a non-default head.
        if (head != DEFAULT_CONTEXT) return listOf(context)
        return listOf(DEFAULT_CONTEXT, tail)
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
