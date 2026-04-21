package cssvarsassistant.util

/**
 * Tiny text-only helpers for CSS-ish strings.
 *
 * Kept intentionally stringly-typed so the same function can serve both
 * index-time and query-time callers without paying for PSI parsing.
 */
object CssTextUtil {

    // Non-greedy, single-pass strip of /* ... */ block comments. Handles
    // multi-line and nested-looking bodies (CSS doesn't allow nesting, but
    // users sometimes write `/* foo /* bar */` — we accept the first `*/`
    // like CSS parsers do).
    private val BLOCK_COMMENT_REGEX = Regex("""/\*[\s\S]*?\*/""")

    // Standard SCSS/Less single-line comments. Stops at newline.
    private val LINE_COMMENT_REGEX = Regex("""//[^\n\r]*""")

    /**
     * Returns [text] with every `/* ... */` block comment and `// ...` line
     * comment removed. The removed span is replaced by a single space so
     * token boundaries don't collapse (e.g. `a/*x*/b` becomes `a b`, not `ab`).
     */
    fun stripCssComments(text: String): String {
        if (text.isEmpty()) return text
        val noBlocks = BLOCK_COMMENT_REGEX.replace(text, " ")
        return LINE_COMMENT_REGEX.replace(noBlocks, " ")
    }
}
