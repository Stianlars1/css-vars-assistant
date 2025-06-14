package cssvarsassistant.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
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
import cssvarsassistant.util.RankUtil.rank
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil

class CssVariableDocumentation : AbstractDocumentationProvider() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

    private val ENTRY_SEP = "|||"

    override fun generateDoc(element: PsiElement, original: PsiElement?): String? {
        try {

            val project = element.project
            if (DumbService.isDumb(project)) return null

            ProgressManager.checkCanceled()
            val settings = CssVarsAssistantSettings.getInstance()
            val varName = extractVariableName(element) ?: return null
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            val rawEntries = FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
                .flatMap { it.split(ENTRY_SEP) }.filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            val parsed: List<Triple<String, String, String>> = rawEntries.mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) {
                    val ctx = p[0]
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


            val hasNonPixelSizeValues = sorted.any { (_, value, _) ->
                (ValueUtil.isSizeValue(value) || ValueUtil.isNumericValue(value)) &&
                        !value.trim().endsWith("px")
            }

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
                .append("<td>&nbsp;</td>") // color swatch column
                .append("<td align='left'>Value</td>")

            // Only add pixel equivalent column if there are size values
            if (hasNonPixelSizeValues) {
                sb.append("<td align='left'>Pixel Equivalent</td>")
            }
            sb.append("</tr>")

            for ((ctx, value, _) in sorted) {
                ProgressManager.checkCanceled()

                val isColour = ColorParser.parseCssColor(value) != null
                val pixelEquivalent = if (ValueUtil.isSizeValue(value)) {
                    "${ValueUtil.convertToPixels(value).toInt()}px"
                } else if (ValueUtil.isNumericValue(value)) {
                    "${value.trim().toDoubleOrNull()?.toInt() ?: 0}px"
                } else {
                    "—"
                }

                sb.append("<tr><td style='color:#888;padding-right:10px'>")
                    .append(StringUtil.escapeXmlEntities(contextLabel(ctx, isColour)))
                    .append("</td><td>")
                if (isColour) sb.append(colorSwatchHtml(value)) else sb.append("&nbsp;")
                sb.append("</td><td style='padding-right:15px'>")
                    .append(StringUtil.escapeXmlEntities(value))
                    .append("</td>")

                // Only add pixel equivalent cell if we're showing that column
                if (hasNonPixelSizeValues) {
                    sb.append("<td style='color:#666;font-size:0.9em'>")
                        .append(StringUtil.escapeXmlEntities(pixelEquivalent))
                        .append("</td>")
                }
                sb.append("</tr>")
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
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }

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

            Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)?.let { m ->
                val ref = m.groupValues[1]
                if (ref !in visited) {
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

                    if (defVal != null)
                        return resolveVarValue(project, defVal, visited + ref, depth + 1)
                }
                return raw
            }

            val lessMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())
            if (lessMatch != null) {
                val varName = lessMatch.groupValues[1]

                val currentScope = ScopeUtil.currentPreprocessorScope(project)
                CssVarCompletionCache.get(project, varName, currentScope)?.let { return it }

                val resolved = findPreprocessorVariableValue(project, varName)

                if (resolved != null) {
                    CssVarCompletionCache.put(project, varName, currentScope, resolved)
                }

                return resolved ?: raw
            }

            return raw
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to resolve variable in documentation: $raw", e)
            return raw
        }
    }

    private fun findPreprocessorVariableValue(
        project: Project,
        varName: String
    ): String? {
        return try {
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

}