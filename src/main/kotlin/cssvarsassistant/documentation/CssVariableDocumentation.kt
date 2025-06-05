package cssvarsassistant.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ArithmeticEvaluator

class CssVariableDocumentation : AbstractDocumentationProvider() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

    private val ENTRY_SEP = "|||"
    private val lessVarCache = mutableMapOf<Pair<Project, String>, String?>()

    override fun generateDoc(element: PsiElement, original: PsiElement?): String? {
        try {

            val project = element.project
            // Check for cancellation early
            if (DumbService.isDumb(project)) return null


            ProgressManager.checkCanceled()
            val settings = CssVarsAssistantSettings.getInstance()
            val varName = extractVariableName(element) ?: return null
            // FIXED: Use CSS indexing scope for FileBasedIndex operations
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            val rawEntries = FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
                .flatMap { it.split(ENTRY_SEP) }.filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            val parsed: List<Triple<String, String, String>> = rawEntries.mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) {
                    val ctx = p[0]
                    // FIXED: Pass project instead of scope to resolveVarValue
                    val value = resolveVarValue(project, p[1])
                    val comment = p.getOrElse(2) { "" }
                    Triple(ctx, value, comment)
                } else null
            }

            val unique = parsed.distinctBy { it.first to it.second }

            val sorted = unique.sortedWith(
                compareBy(
                    { rank(it.first).first },
                    { rank(it.first).second ?: Int.MAX_VALUE },
                    { rank(it.first).third }
                )
            )

            val docEntry =
                unique.firstOrNull { it.third.isNotBlank() } ?: unique.find { it.first == "default" } ?: unique.first()
            val doc = DocParser.parse(docEntry.third, docEntry.second)

            val sb = StringBuilder()
            sb.append("<html><body>").append(DocumentationMarkup.DEFINITION_START)
            if (doc.name.isNotBlank()) sb.append("<b>").append(StringUtil.escapeXmlEntities(doc.name))
                .append("</b><br/>")
            sb.append("<small>CSS Variable: <code>").append(StringUtil.escapeXmlEntities(varName))
                .append("</code></small>").append(DocumentationMarkup.DEFINITION_END)
                .append(DocumentationMarkup.CONTENT_START)

            sb.append("<p><b>Values:</b></p>")
                .append("<table>")
                .append("<tr><td>Context</td>")
                .append("<td>&nbsp;</td>")
                .append("<td align='left'>Value</td></tr>")

            for ((ctx, value, _) in sorted) {
                // Check for cancellation in loops
                ProgressManager.checkCanceled()

                val isColour = ColorParser.parseCssColor(value) != null
                sb.append("<tr><td style='color:#888;padding-right:10px'>")
                    .append(StringUtil.escapeXmlEntities(contextLabel(ctx, isColour)))
                    .append("</td><td>")
                if (isColour) sb.append(colorSwatchHtml(value)) else sb.append("&nbsp;")
                sb.append("</td><td>")
                    .append(StringUtil.escapeXmlEntities(value))
                    .append("</td></tr>")
            }
            sb.append("</table>")

            if (doc.description.isNotBlank()) sb.append("<p><b>Description:</b><br/>")
                .append(StringUtil.escapeXmlEntities(doc.description)).append("</p>")

            if (doc.examples.isNotEmpty()) {
                sb.append("<p><b>Examples:</b></p><pre>")
                doc.examples.forEach { sb.append(StringUtil.escapeXmlEntities(it)).append('\n') }
                sb.append("</pre>")
            }

            sorted.mapNotNull { ColorParser.parseCssColor(it.second) }.firstOrNull()?.let { c ->
                val hex = "%02x%02x%02x".format(c.red, c.green, c.blue)
                sb.append(
                    """<p style='margin-top:10px'>
                             |<a target="_blank"
                             |   href="https://webaim.org/resources/contrastchecker/?fcolor=$hex&bcolor=000000">
                             |Check contrast on WebAIM Contrast Checker
                             |</a></p>""".trimMargin()
                )
            }

            sb.append(DocumentationMarkup.CONTENT_END).append("</body></html>")

            return sb.toString()
        } catch (e: ProcessCanceledException) {
            // CRITICAL: Always rethrow ProcessCanceledException
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }

    // FIXED: Always use fresh scope for resolution

private fun resolveVarValue(
    project: Project,
    raw: String,
    visited: Set<String> = emptySet(),
    depth: Int = 0,
): String {
    val settings = CssVarsAssistantSettings.getInstance()
    if (depth > settings.maxImportDepth) return raw

    try {
        ProgressManager.checkCanceled()

        var result = raw

        val cssVarRegex = Regex("""var\(\s*(--[\w-]+)\s*\)""")
        var changed = true
        while (changed) {
            changed = false
            cssVarRegex.findAll(result).toList().asReversed().forEach { m ->
                val ref = m.groupValues[1]
                if (ref in visited) return@forEach

                val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)
                val entries = FileBasedIndex.getInstance()
                    .getValues(CSS_VARIABLE_INDEXER_NAME, ref, cssScope)
                    .flatMap { it.split(ENTRY_SEP) }
                    .filter { it.isNotBlank() }

                val defVal = entries.mapNotNull {
                    val p = it.split(DELIMITER, limit = 3)
                    if (p.size >= 2) p[0] to p[1] else null
                }.let { list ->
                    list.find { it.first == "default" }?.second ?: list.firstOrNull()?.second
                }

                if (defVal != null) {
                    val resolved = resolveVarValue(project, defVal, visited + ref, depth + 1)
                    result = result.replaceRange(m.range, resolved)
                    changed = true
                }
            }
        }

        val preprocRegex = Regex("[@$]([\\w-]+)")
        changed = true
        while (changed) {
            changed = false
            preprocRegex.findAll(result).toList().asReversed().forEach { m ->
                val varName = m.groupValues[1]
                if (varName in visited) return@forEach

                val currentScope = ScopeUtil.currentPreprocessorScope(project)
                val cached = CssVarCompletionCache.get(project, varName, currentScope)
                val resolved = cached ?: findPreprocessorVariableValue(project, varName)
                if (resolved != null) {
                    CssVarCompletionCache.put(project, varName, currentScope, resolved)
                    result = result.replaceRange(m.range, resolved)
                    changed = true
                }
            }
        }

        val trimmed = result.trim()
        val calcInside = Regex("^calc\\((.*)\\)$").matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed

        val evaluated = ArithmeticEvaluator.evaluate(calcInside)
        return evaluated ?: calcInside
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        logger.warn("Failed to resolve variable in documentation: $raw", e)
        return raw
    }
}
    // FIXED: Always use fresh scope for preprocessor resolution
    private fun findPreprocessorVariableValue(
        project: Project,
        varName: String
    ): String? {
        return try {
            // Always compute fresh scope to see newly discovered imports
            val freshScope = ScopeUtil.currentPreprocessorScope(project)
            PreprocessorUtil.resolveVariable(project, varName, freshScope)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to find preprocessor variable in doc-menu: $varName", e)
            null
        }
    }

    private fun contextLabel(ctx: String, isColor: Boolean): String = when {
        ctx == "default" -> {
            if (isColor) "Light mode" else "Default"
        }

        "prefers-color-scheme" in ctx.lowercase() && "light" in ctx.lowercase() -> "Light mode"
        "prefers-color-scheme" in ctx.lowercase() && "dark" in ctx.lowercase() -> "Dark mode"
        Regex("""max-width:\s*(\d+)""").find(ctx) != null -> "≤${Regex("""max-width:\s*(\d+)""").find(ctx)!!.groupValues[1]}px"
        Regex("""min-width:\s*(\d+)""").find(ctx) != null -> "≥${Regex("""min-width:\s*(\d+)""").find(ctx)!!.groupValues[1]}px"
        else -> ctx
    }

    private fun extractVariableName(el: PsiElement): String? = el.text.trim().takeIf { it.startsWith("--") }
        ?: el.parent?.text?.let { Regex("""var\(\s*(--[\w-]+)\s*\)""").find(it)?.groupValues?.get(1) }

    private fun colorSwatchHtml(cssValue: String): String {
        val c = ColorParser.parseCssColor(cssValue) ?: return "&nbsp;"
        val hex = "#%02x%02x%02x".format(c.red, c.green, c.blue)
        return """<font color="$hex">&#9632;</font>"""
    }

    private fun rank(ctx: String): Triple<Int, Int?, String> {
        val c = ctx.lowercase()

        if (c == "default" || ("prefers-color-scheme" in c && "light" in c)) return Triple(0, null, c)

        if ("prefers-color-scheme" in c && "dark" in c) return Triple(1, null, c)

        Regex("""max-width:\s*(\d+)(px|rem|em)?""").find(c)?.let {
            return Triple(2, -it.groupValues[1].toInt(), c)
        }
        Regex("""min-width:\s*(\d+)(px|rem|em)?""").find(c)?.let {
            return Triple(3, it.groupValues[1].toInt(), c)
        }

        if (arrayOf("hover", "motion", "orientation", "print").any { it in c }) return Triple(4, null, c)

        return Triple(5, null, c)
    }
}

/** Runs [action] only when indices are ready, otherwise returns an empty list. */
private inline fun <T> safeIndexLookup(project: Project, action: () -> List<T>): List<T> {
    return if (DumbService.isDumb(project)) emptyList() else try {
        action()
    } catch (ignored: IndexNotReadyException) {           // ➌ safety-net
        emptyList()
    }
}