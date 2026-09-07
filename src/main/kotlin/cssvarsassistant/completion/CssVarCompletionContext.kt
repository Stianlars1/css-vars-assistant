package cssvarsassistant.completion

import com.intellij.openapi.progress.ProgressManager
import cssvarsassistant.util.CssTextUtil

/** Bare custom-property names belong only in the first argument of var(). */
internal object CssVarCompletionContext {
    fun isFirstArgument(source: String, offset: Int): Boolean {
        ProgressManager.checkCanceled()
        if (offset !in 0..source.length) return false
        val masked = CssTextUtil.maskComments(source, maskStrings = true).text
        var probe = offset - 1
        while (probe >= 0 && source[probe].isWhitespace()) probe--
        if (probe >= 0 && source[probe] != masked[probe]) return false

        // The nearest opening parenthesis also excludes functions nested in a
        // fallback, while allowing an explicit nested var(--name, var(--...)).
        val opening = masked.lastIndexOf('(', offset - 1)
        if (opening < 0) return false
        val argument = masked.substring(opening + 1, offset)
        if (argument.any { it in ",);{}[]" }) return false

        var nameEnd = opening
        while (nameEnd > 0 && masked[nameEnd - 1].isWhitespace()) nameEnd--
        val nameStart = nameEnd - 3
        if (nameStart < 0 || !masked.regionMatches(nameStart, "var", 0, 3, ignoreCase = true)) return false
        val preceding = masked.getOrNull(nameStart - 1)
        return preceding == null || !(preceding.isLetterOrDigit() || preceding in "_-\\.")
    }
}
