package cssvarsassistant.index

internal data class ParsedCssVariableEntry(
    val name: String,
    val context: String,
    val value: String,
    val comment: String
)

internal object CssVariableEntryParser {

    private const val DEFAULT_CONTEXT = "default"
    private val variableDeclarationRegex = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*([^;]+);""")
    private val variableDeclarationStartRegex = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*(.*)$""")

    // Single-line `/* ... */` comments embedded inside an otherwise normal
    // line. Multi-line block comments are handled separately via the
    // `inBlockComment` state machine below.
    private val inlineBlockCommentRegex = Regex("""/\*.*?\*/""")

    // SCSS/Less `// comment` to end of line. CSS itself doesn't support
    // `//` comments but the Jetbrains CSS plugin treats them as such inside
    // SCSS/Less files, so we strip them here too.
    private val lineCommentRegex = Regex("""//[^\n]*$""")

    private data class MediaContext(
        val label: String,
        val depthAfterOpen: Int
    )

    fun parse(text: CharSequence): List<ParsedCssVariableEntry> {
        val entries = mutableListOf<ParsedCssVariableEntry>()
        val mediaContexts = ArrayDeque<MediaContext>()
        var braceDepth = 0
        var pendingMediaContext: String? = null

        var lastComment: String? = null
        var inBlockComment = false
        val blockComment = StringBuilder()
        var pendingVariableName: String? = null
        var pendingVariableContext: String? = null
        var pendingVariableComment: String? = null
        val pendingVariableValue = StringBuilder()

        for (rawLine in text.lines()) {
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
                pendingMediaContext = mediaContext
            }

            val openingBraces = line.count { it == '{' }
            val closingBraces = line.count { it == '}' }

            if (pendingMediaContext != null && openingBraces > 0) {
                mediaContexts.addLast(MediaContext(pendingMediaContext, braceDepth + 1))
                pendingMediaContext = null
            }

            val currentContext = mediaContexts.lastOrNull()?.label ?: DEFAULT_CONTEXT
            if (pendingVariableName != null) {
                val endIndex = line.indexOf(';')
                if (endIndex >= 0) {
                    if (pendingVariableValue.isNotEmpty()) {
                        pendingVariableValue.append('\n')
                    }
                    pendingVariableValue.append(line.substring(0, endIndex).trim())
                    entries += ParsedCssVariableEntry(
                        name = requireNotNull(pendingVariableName),
                        context = requireNotNull(pendingVariableContext),
                        value = normalizeValue(pendingVariableValue.toString()),
                        comment = pendingVariableComment.orEmpty()
                    )
                    pendingVariableName = null
                    pendingVariableContext = null
                    pendingVariableComment = null
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
                    entries += ParsedCssVariableEntry(
                        name = match.groupValues[1],
                        context = currentContext,
                        value = match.groupValues[2].trim(),
                        comment = if (index == 0) lastComment ?: "" else ""
                    )
                }

                if (matches.isNotEmpty()) {
                    lastComment = null
                } else {
                    val multilineMatch = variableDeclarationStartRegex.find(line)
                    if (multilineMatch != null && ';' !in multilineMatch.groupValues[2]) {
                        pendingVariableName = multilineMatch.groupValues[1]
                        pendingVariableContext = currentContext
                        pendingVariableComment = lastComment
                        pendingVariableValue.clear()
                        pendingVariableValue.append(multilineMatch.groupValues[2].trim())
                    }
                }
            }

            braceDepth = (braceDepth + openingBraces - closingBraces).coerceAtLeast(0)
            while (mediaContexts.isNotEmpty() && mediaContexts.last().depthAfterOpen > braceDepth) {
                mediaContexts.removeLast()
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

    private fun normalizeValue(value: String): String =
        value
            .lineSequence()
            .joinToString(" ") { it.trim() }
            .replace(Regex("""\s+"""), " ")
            .trim()
}
