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

    // IntelliJ's completion framework injects this dummy identifier into the
    // copy of the file it hands to contributors (see `CompletionUtilCore`).
    // It looks harmless until you read `params.originalFile.text` and the
    // dummy lands right next to the prefix the user actually typed.
    private const val INTELLIJ_COMPLETION_DUMMY = "IntellijIdeaRulezzz"

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

    /**
     * Returns [text] with any IntelliJ completion dummy identifier
     * (`IntellijIdeaRulezzz` — potentially with a trailing space) removed.
     *
     * Use this at any call site that reads `params.originalFile.text`
     * (which is the copy file with the dummy injected) before running
     * string/regex logic that assumes the text is what the user typed.
     */
    fun stripCompletionDummy(text: String): String {
        if (text.isEmpty()) return text
        val idx = text.indexOf(INTELLIJ_COMPLETION_DUMMY, ignoreCase = true)
        if (idx < 0) return text
        val after = idx + INTELLIJ_COMPLETION_DUMMY.length
        // The dummy is usually followed by a single space; drop it too so we
        // don't leave `-- ` where the user had `--` followed by nothing.
        val tail = if (after < text.length && text[after] == ' ') after + 1 else after
        return text.substring(0, idx) + text.substring(tail)
    }
}
