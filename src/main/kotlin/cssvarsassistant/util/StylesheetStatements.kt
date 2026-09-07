package cssvarsassistant.util

/** Statements split only at structural delimiters, never inside strings or values. */
internal data class StylesheetStatement(
    val text: String,
    val offset: Int,
    val line: Int,
    val terminator: Char,
    val comment: String,
    val indent: Int
)

internal object StylesheetStatements {
    private val variableStart = Regex("""^(?:--|[$@])[\p{L}\p{N}_-]+\s*:""")

    fun parse(source: String, extension: String? = null): List<StylesheetStatement> {
        val masked = CssTextUtil.maskComments(source, lineComments = extension != "css")
        val text = masked.text
        val statements = mutableListOf<StylesheetStatement>()
        var quote: Char? = null
        var escaped = false
        var parentheses = 0
        var brackets = 0
        var valueBraces = 0
        var first = -1
        var firstLine = 1
        var line = 1
        var lineStart = 0
        var indent = 0
        var boundary = 0
        var commentIndex = 0

        fun emit(end: Int, terminator: Char) {
            var comment = ""
            while (commentIndex < masked.comments.size && masked.comments[commentIndex].end <= (if (first >= 0) first else end)) {
                val candidate = masked.comments[commentIndex++]
                if (candidate.start >= boundary && candidate.text.isNotEmpty()) comment = candidate.text
            }
            if (first >= 0 || terminator == '}') {
                statements += StylesheetStatement(
                    if (first >= 0) text.substring(first, end).trim() else "",
                    if (first >= 0) first else end, firstLine, terminator, comment, indent
                )
            }
            first = -1
            boundary = end + 1
        }

        for (i in text.indices) {
            val ch = text[i]
            if (first < 0 && !ch.isWhitespace() && ch != ';' && ch != '}') {
                first = i
                firstLine = line
                indent = source.substring(lineStart, i).takeWhile { it == ' ' || it == '\t' }.fold(0) { total, ch -> total + if (ch == '\t') 4 else 1 }
            }
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == quote -> quote = null
                }
            } else {
                when (ch) {
                    '\'', '"' -> quote = ch
                    '(' -> parentheses++
                    ')' -> parentheses = (parentheses - 1).coerceAtLeast(0)
                    '[' -> brackets++
                    ']' -> brackets = (brackets - 1).coerceAtLeast(0)
                    '{' -> {
                        if (valueBraces > 0 || (i > 0 && text[i - 1] in "#@") ||
                            (first >= 0 && variableStart.containsMatchIn(text.substring(first, i)))) valueBraces++
                        else if (parentheses == 0 && brackets == 0) emit(i, '{')
                    }
                    '}' -> {
                        if (valueBraces > 0) valueBraces--
                        else if (parentheses == 0 && brackets == 0) emit(i, '}')
                    }
                    ';' -> if (parentheses == 0 && brackets == 0 && valueBraces == 0) emit(i, ';')
                    '\n' -> if (extension == "sass" && parentheses == 0 && brackets == 0 && valueBraces == 0) emit(i, '\n')
                }
            }
            if (ch == '\n') { line++; lineStart = i + 1 }
        }
        if (first >= 0) emit(text.length, '\u0000')
        return statements
    }
}
