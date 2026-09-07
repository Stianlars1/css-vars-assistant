package cssvarsassistant.documentation

import com.intellij.lang.css.CssLanguageProperties
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssFunction
import com.intellij.psi.util.PsiTreeUtil
import cssvarsassistant.util.CssTextUtil
import cssvarsassistant.util.StylesheetStatements

/** Editor queries use the selected PSI token; they never select a nearby variable. */
internal object VariablePsiContext {
    private val cssName = Regex("""--[\p{L}\p{N}_-]+""")
    private val preprocessorName = Regex("""[$@][\p{L}\p{N}_-]+""")
    private val propertyHead = Regex("""(?:[$@]|--)?[\p{L}_-][\p{L}\p{N}_-]*\s*""")
    private val sassExpressionDirective = Regex("""@(include|return|if|else\s+if|while|for|each|debug|warn|error)\s+.*""", RegexOption.DOT_MATCHES_ALL)

    /** Qualified Sass references retain their namespace, for example `theme.$brand`. */
    fun variableName(element: PsiElement): String? {
        if (!element.isValid) return null
        val file = element.containingFile ?: return null
        if (element.language !is CssLanguageProperties && file.language !is CssLanguageProperties) return null
        if (PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false) != null) return null
        if (element is CssDeclaration && element.isCustomProperty) return element.propertyName
        if (element is PsiNameIdentifierOwner) {
            val identifier = element.nameIdentifier
            if (identifier != null && identifier != element) return variableName(identifier)
        }

        val token = generateSequence(element) { it.parent }
            .takeWhile { it != file }
            .firstOrNull {
                val type = it.node?.elementType
                val childType = it.firstChild?.node?.elementType
                isVariableToken(type) || (isVariableToken(childType) && preprocessorName.matches(it.text))
            }
        if (token != null) {
            val name = token.text.takeIf(preprocessorName::matches) ?: return null
            if (!name.startsWith('$')) return name
            val source = file.text
            val start = token.textRange.startOffset
            if (start > 1 && source[start - 1] == '.') {
                var namespaceStart = start - 1
                while (namespaceStart > 0 && isNameChar(source[namespaceStart - 1])) namespaceStart--
                if (namespaceStart < start - 1) return source.substring(namespaceStart, start) + name
            }
            return name
        }

        val name = element.text?.takeIf(cssName::matches) ?: return null
        val declaration = PsiTreeUtil.getParentOfType(element, CssDeclaration::class.java, false)
        if (declaration?.propertyNameElement?.textRange?.contains(element.textRange) == true) return name
        val function = PsiTreeUtil.getParentOfType(element, CssFunction::class.java, false) ?: return null
        if (!function.name.equals("var", ignoreCase = true)) return null
        val firstTerm = function.value?.terms?.firstOrNull() ?: return null
        return name.takeIf { firstTerm.textRange.contains(element.textRange) && firstTerm.text == name }
    }

    fun isPreprocessorValuePosition(extension: String?, text: String, symbolOffset: Int): Boolean {
        val dialect = extension?.lowercase() ?: return false
        val symbol = text.getOrNull(symbolOffset) ?: return false
        if (when (dialect) { "scss", "sass" -> symbol != '$'; "less" -> symbol != '@'; else -> true }) return false
        val masked = CssTextUtil.maskComments(text, lineComments = true).text
        if (masked[symbolOffset] != symbol || isEscaped(text, symbolOffset)) return false
        val lexical = lexicalPosition(masked, symbolOffset, dialect)
        if (lexical == Position.COMMENT) return false
        if (dialect == "less" && text.getOrNull(symbolOffset + 1) == '{') return true
        if (lexical == Position.STRING) return false
        if (lexical == Position.INTERPOLATION) return true

        val before = masked.substring(0, symbolOffset)
        val statements = StylesheetStatements.parse(before, dialect)
        val statement = statements.lastOrNull() ?: return false
        if (dialect == "sass" && statement.terminator == '\n' && statement.text.endsWith(':')) {
            val linePrefix = before.substringAfterLast('\n')
            val indent = linePrefix.fold(0) { total, ch -> total + if (ch == '\t') 4 else 1 }
            return linePrefix.isBlank() && indent > statement.indent && propertyHead.matches(statement.text.dropLast(1))
        }
        if (statement.terminator != '\u0000') return false
        val prefix = before.substring(statement.offset)
        val colon = prefix.indexOf(':')
        if (colon >= 0 && propertyHead.matches(prefix.substring(0, colon))) {
            val containingStatement = StylesheetStatements.parse(masked, dialect).lastOrNull { it.offset <= symbolOffset }
            return containingStatement?.terminator != '{'
        }
        if (dialect != "less" && sassExpressionDirective.matches(prefix)) return true
        return false
    }

    private enum class Position { CODE, STRING, INTERPOLATION, COMMENT }

    /** Interpolation suspends the surrounding quote until its matching closing brace. */
    private fun lexicalPosition(text: String, offset: Int, dialect: String): Position {
        var quote: Char? = null
        var escaped = false
        val interpolationQuotes = mutableListOf<Char?>()
        var i = 0
        while (i < offset) {
            val ch = text[i]
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (quote == null && interpolationQuotes.isNotEmpty() && ch == '/' && text.getOrNull(i + 1) in listOf('*', '/')) {
                val end = if (text[i + 1] == '*') {
                    text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                } else {
                    text.indexOfAny(charArrayOf('\r', '\n'), i + 2).let { if (it < 0) text.length else it }
                }
                if (offset < end) return Position.COMMENT
                i = end
                continue
            } else if ((ch == '#' && dialect != "less" || ch == '@' && dialect == "less") && text.getOrNull(i + 1) == '{') {
                interpolationQuotes += quote
                quote = null
                i++
            } else if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '\'' || ch == '"') {
                quote = ch
            } else if (ch == '}' && interpolationQuotes.isNotEmpty()) {
                quote = interpolationQuotes.removeAt(interpolationQuotes.lastIndex)
            }
            i++
        }
        return when {
            quote != null -> Position.STRING
            interpolationQuotes.isNotEmpty() -> Position.INTERPOLATION
            else -> Position.CODE
        }
    }

    private fun isNameChar(ch: Char): Boolean = ch.isLetterOrDigit() || ch == '_' || ch == '-'

    private fun isVariableToken(type: com.intellij.psi.tree.IElementType?): Boolean =
        // Sass shares SCSS_VARIABLE. Compare the emitted token contract without
        // linking optional Sass/LESS plugin classes on CSS-only installations.
        type?.language is CssLanguageProperties && (type.toString() == "SCSS_VARIABLE" || type.toString() == "LESS_VARIABLE")

    private fun isEscaped(text: String, offset: Int): Boolean {
        var start = offset
        while (start > 0 && text[start - 1] == '\\') start--
        return (offset - start) % 2 != 0
    }
}
