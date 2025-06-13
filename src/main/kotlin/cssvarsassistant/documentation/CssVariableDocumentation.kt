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
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.RankUtil.rank
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil

data class ResolutionInfo(val original: String, val resolved: String, val steps: List<String> = emptyList())

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

            val parsed: List<Triple<String, ResolutionInfo, String>> = rawEntries.mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) {
                    val ctx = p[0]
                    val resInfo = resolveVarValue(project, p[1])
                    val comment = p.getOrElse(2) { "" }
                    Triple(ctx, resInfo, comment)
                } else null
            }

            val collapsed = parsed
                .groupBy { it.first } // Group by context (default, @media, etc.)
                .mapValues { (_, list) -> list.last() } // Last declared wins within each context
                .values.toList()

            val unique = collapsed

            val sorted = unique.sortedWith(
                compareBy(
                    { rank(it.first).first },
                    { rank(it.first).second ?: Int.MAX_VALUE },
                    { rank(it.first).third }
                )
            )

            val docEntry = unique.firstOrNull { it.third.isNotBlank() }
                ?: unique.find { it.first == "default" }
                ?: unique.first()
            val doc = DocParser.parse(docEntry.third, docEntry.second.resolved)

            val hasNonPixelSizeValues = sorted.any { (_, resInfo, _) ->
                (ValueUtil.isSizeValue(resInfo.resolved) || ValueUtil.isNumericValue(resInfo.resolved)) ||
                        (resInfo.steps.isNotEmpty() && resInfo.original != resInfo.resolved)
            }

            val sb = StringBuilder()
            sb.append("<html><head><style>")
                .append("body { font-size: 11px; line-height: 1.3; }")
                .append("table { font-size: 10px; }")
                .append("td { padding: 2px 4px; max-width: 200px; word-wrap: break-word; }")
                .append("code { font-size: 10px; }")
                .append("</style></head><body>")
                .append(DocumentationMarkup.DEFINITION_START)


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

            if (hasNonPixelSizeValues) {
                sb.append("<td align='left'>Pixel Equivalent</td>")
            }
            sb.append("</tr>")

            for ((ctx, resInfo, _) in sorted) {
                ProgressManager.checkCanceled()

                val value = resInfo.resolved
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

                if (resInfo.steps.isNotEmpty() && resInfo.original != resInfo.resolved) {
                    sb.append(StringUtil.escapeXmlEntities(resInfo.original))
                } else {
                    sb.append(StringUtil.escapeXmlEntities(value))
                }
                sb.append("</td>")

                if (hasNonPixelSizeValues) {
                    val displayPixel = if (ValueUtil.isSizeValue(resInfo.resolved)) {
                        pixelEquivalent // Show converted pixels, not resolved value
                    } else if (resInfo.steps.isNotEmpty() && resInfo.original != resInfo.resolved) {
                        resInfo.resolved // Show any resolved value
                    } else {
                        pixelEquivalent // Show converted equivalent
                    }
                    sb.append("<td style='color:#666;font-size:0.9em'>")
                        .append(StringUtil.escapeXmlEntities(displayPixel))
                        .append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</table>")

            val (fileCount, usageCount) = getUsageStats(project, varName)
            if (fileCount > 0) {
                sb.append("<p><b>Usage:</b> ${usageCount} times in ${fileCount} files</p>")
            }

            val dependencies = findDependencies(project, varName)
            if (dependencies.isNotEmpty()) {
                sb.append("<p><b>References:</b> ")
                dependencies.take(5).forEach { dep ->
                    sb.append("<code>${StringUtil.escapeXmlEntities(dep)}</code> ")
                }
                if (dependencies.size > 5) sb.append("...")
                sb.append("</p>")
            }

            val related = findRelatedVariables(project, varName)
            if (related.isNotEmpty()) {
                sb.append("<p><b>Related:</b> ")
                related.forEach { rel ->
                    sb.append("<code>${StringUtil.escapeXmlEntities(rel.removePrefix("--"))}</code> ")
                }
                sb.append("</p>")
            }

            val locations = getFileLocations(project, varName)
            if (locations.isNotEmpty()) {
                sb.append("<p><b>Files:</b> ${locations.joinToString(", ")}</p>")
            }


            if (doc.description.isNotBlank()) sb.append("<p><b>Description:</b><br/>")
                .append(StringUtil.escapeXmlEntities(doc.description)).append("</p>")


            if (doc.examples.isNotEmpty()) {
                sb.append("<p><b>Examples:</b></p><pre>")
                doc.examples.forEach { sb.append(StringUtil.escapeXmlEntities(it)).append('\n') }
                sb.append("</pre>")
            }

            sorted.mapNotNull { ColorParser.parseCssColor(it.second.resolved) }.firstOrNull()?.let { c ->
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
        steps: List<String> = emptyList()
    ): ResolutionInfo {
        val settings = CssVarsAssistantSettings.getInstance()
        if (depth > settings.maxImportDepth) return ResolutionInfo(raw, raw, steps)

        try {
            ProgressManager.checkCanceled()

            Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)?.let { m ->
                val ref = m.groupValues[1]
                if (ref !in visited) {
                    val newSteps = steps + "var($ref)"
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
                        return resolveVarValue(project, defVal, visited + ref, depth + 1, newSteps)
                }
                return ResolutionInfo(raw, raw, steps)
            }

            val preprocessorMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())
            if (preprocessorMatch != null) {
                val varName = preprocessorMatch.groupValues[1]
                val prefix = if (raw.contains("@")) "@" else "$"
                val currentScope = ScopeUtil.currentPreprocessorScope(project)

                CssVarCompletionCache.get(project, varName, currentScope)?.let {
                    return ResolutionInfo(raw, it, steps + "$prefix$varName")
                }

                val resolved = findPreprocessorVariableValue(project, varName)
                if (resolved != null && resolved != raw) {
                    CssVarCompletionCache.put(project, varName, currentScope, resolved)
                    return ResolutionInfo(raw, resolved, steps + "$prefix$varName")
                }
            }

            return ResolutionInfo(raw, raw, steps)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to resolve variable in documentation: $raw", e)
            return ResolutionInfo(raw, raw, steps)
        }
    }

    private fun findPreprocessorVariableValue(project: Project, varName: String): String? {
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

    private fun contextLabel(ctx: String, isColor: Boolean): String {
        // Handle default case
        if (ctx == "default") return if (isColor) "Light mode" else "Default"

        // Handle color scheme
        if ("prefers-color-scheme" in ctx.lowercase()) {
            return when {
                "light" in ctx.lowercase() -> "Light mode"
                "dark" in ctx.lowercase() -> "Dark mode"
                else -> "Color scheme"
            }
        }

        // Handle reduced motion
        if ("prefers-reduced-motion" in ctx.lowercase() && "reduce" in ctx.lowercase()) {
            return "Reduced motion"
        }

        // Handle width-based media queries with better regex
        val maxWidthRegex = Regex("""max-width:\s*(\d+)(?:px)?\s*\)""")
        val minWidthRegex = Regex("""min-width:\s*(\d+)(?:px)?\s*\)""")

        val maxWidthMatch = maxWidthRegex.find(ctx)
        val minWidthMatch = minWidthRegex.find(ctx)

        return when {
            maxWidthMatch != null && minWidthMatch != null -> {
                val maxWidth = maxWidthMatch.groupValues[1]
                val minWidth = minWidthMatch.groupValues[1]
                "${minWidth}px-${maxWidth}px"
            }

            maxWidthMatch != null -> "≤${maxWidthMatch.groupValues[1]}px"
            minWidthMatch != null -> "≥${minWidthMatch.groupValues[1]}px"
            else -> {
                // Clean up context for display
                ctx.replace(Regex("""@media\s+"""), "")
                    .replace("screen and ", "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                    .takeIf { it.isNotEmpty() } ?: "Media query"
            }
        }
    }

    private fun extractVariableName(el: PsiElement): String? = el.text.trim().takeIf { it.startsWith("--") }
        ?: el.parent?.text?.let { Regex("""var\(\s*(--[\w-]+)\s*\)""").find(it)?.groupValues?.get(1) }

    private fun colorSwatchHtml(cssValue: String): String {
        val c = ColorParser.parseCssColor(cssValue) ?: return "&nbsp;"
        val hex = "#%02x%02x%02x".format(c.red, c.green, c.blue)
        return """<font color="$hex">&#9632;</font>"""
    }

    private fun getUsageStats(project: Project, varName: String): Pair<Int, Int> {
        val settings = CssVarsAssistantSettings.getInstance()
        val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

        val files = FileBasedIndex.getInstance()
            .getContainingFiles(CSS_VARIABLE_INDEXER_NAME, varName, scope)

        val totalUsages = files.sumOf { file ->
            try {
                file.inputStream.use { stream ->
                    val content = stream.bufferedReader().readText()
                    Regex("""var\(\s*${Regex.escape(varName)}\s*\)""").findAll(content).count()
                }
            } catch (e: Exception) { 0 }
        }

        return files.size to totalUsages
    }

    private fun findDependencies(project: Project, varName: String, visited: Set<String> = emptySet()): List<String> {
        if (varName in visited) return emptyList()

        val settings = CssVarsAssistantSettings.getInstance()
        val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

        val entries = FileBasedIndex.getInstance()
            .getValues(CSS_VARIABLE_INDEXER_NAME, varName, scope)
            .flatMap { it.split(ENTRY_SEP) }
            .filter { it.isNotBlank() }

        return entries.flatMap { entry ->
            val value = entry.split(DELIMITER).getOrNull(1) ?: ""
            Regex("""var\(\s*(--[\w-]+)\s*\)""").findAll(value)
                .map { it.groupValues[1] }
                .flatMap { ref -> listOf(ref) + findDependencies(project, ref, visited + varName) }
        }.distinct()
    }

    private fun findRelatedVariables(project: Project, varName: String): List<String> {
        val keyCache = CssVarKeyCache.get(project)
        val base = varName.removePrefix("--")

        return keyCache.getKeys()
            .filter { it != varName }
            .filter { candidate ->
                val candidateBase = candidate.removePrefix("--")
                // Same prefix or suffix patterns
                base.split("-").any { part ->
                    part.length > 2 && candidateBase.contains(part, ignoreCase = true)
                }
            }
            .take(8)
    }

    private fun getFileLocations(project: Project, varName: String): List<String> {
        val settings = CssVarsAssistantSettings.getInstance()
        val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

        return FileBasedIndex.getInstance()
            .getContainingFiles(CSS_VARIABLE_INDEXER_NAME, varName, scope)
            .map { it.name }
            .distinct()
            .take(5)
    }
}
