package cssvarsassistant.util

/** Text operations shared by indexing and editor queries. Offsets survive masking. */
object CssTextUtil {
    data class Comment(val start: Int, val end: Int, val text: String)
    data class MaskedText(val text: String, val comments: List<Comment>)

    fun maskComments(text: String, lineComments: Boolean = true, maskStrings: Boolean = false): MaskedText {
        val chars = text.toCharArray()
        val comments = mutableListOf<Comment>()
        var quote: Char? = null
        var escaped = false
        var urlDepth = 0
        var i = 0
        fun blank(start: Int, end: Int) {
            for (n in start until end) if (chars[n] != '\n' && chars[n] != '\r') chars[n] = ' '
        }
        while (i < text.length) {
            val ch = text[i]
            if (quote != null) {
                if (maskStrings && ch != '\n' && ch != '\r') chars[i] = ' '
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == quote -> quote = null
                }
                i++
                continue
            }
            if (ch == '\'' || ch == '"') {
                quote = ch
                if (maskStrings) chars[i] = ' '
                i++
                continue
            }
            if (ch == '(') {
                if (urlDepth > 0 || text.substring(maxOf(0, i - 3), i).equals("url", true)) urlDepth++
            } else if (ch == ')' && urlDepth > 0) urlDepth--
            if (ch == '/' && i + 1 < text.length) {
                val block = text[i + 1] == '*'
                val line = lineComments && text[i + 1] == '/' && urlDepth == 0 && (i == 0 || text[i - 1] != ':')
                if (block || line) {
                    val end = if (block) text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    else text.indexOfAny(charArrayOf('\r', '\n'), i + 2).let { if (it < 0) text.length else it }
                    comments += Comment(i, end, if (block) text.substring(i + 2, end).removeSuffix("*/").removePrefix("*").trim() else "")
                    blank(i, end)
                    i = end
                    continue
                }
            }
            i++
        }
        return MaskedText(String(chars), comments)
    }

    fun stripCssComments(text: String): String = maskComments(text).text

    fun isCodeAt(text: String, offset: Int): Boolean {
        if (offset !in text.indices) return false
        val masked = maskComments(text, maskStrings = true).text
        return masked[offset] == text[offset] && !text[offset].isWhitespace()
    }

    fun normalizeWhitespace(text: String): String {
        val result = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var space = false
        for (ch in text.trim()) {
            if (quote != null) {
                result.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == quote -> quote = null
                }
            } else if (ch.isWhitespace()) {
                space = true
            } else {
                if (space && result.isNotEmpty()) result.append(' ')
                space = false
                result.append(ch)
                if (ch == '\'' || ch == '"') quote = ch
            }
        }
        return result.toString()
    }

    fun stripCompletionDummy(text: String): String {
        val dummy = "IntellijIdeaRulezzz"
        val index = text.indexOf(dummy, ignoreCase = true)
        if (index < 0) return text
        val after = index + dummy.length
        val end = if (after < text.length && text[after] == ' ') after + 1 else after
        return text.removeRange(index, end)
    }
}
