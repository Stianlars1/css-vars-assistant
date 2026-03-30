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

        for (rawLine in text.lines()) {
            val line = rawLine.trim()

            if (!inBlockComment && (line.startsWith("/*") || line.startsWith("/**"))) {
                inBlockComment = true
                blockComment.clear()
                if (line.contains("*/")) {
                    blockComment.append(
                        line
                            .removePrefix("/**").removePrefix("/*")
                            .substringBefore("*/")
                            .trim()
                    )
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                } else {
                    blockComment.append(
                        line
                            .removePrefix("/**").removePrefix("/*").trim()
                    )
                }
                continue
            }

            if (inBlockComment) {
                if (line.contains("*/")) {
                    blockComment.append("\n" + line.substringBefore("*/"))
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                } else {
                    blockComment.append("\n" + line)
                }
                continue
            }

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
}
