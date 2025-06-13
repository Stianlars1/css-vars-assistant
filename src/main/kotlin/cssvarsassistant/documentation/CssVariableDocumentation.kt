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
import kotlin.math.roundToInt

data class ResolutionInfo(val original: String, val resolved: String, val steps: List<String> = emptyList())

class CssVariableDocumentation : AbstractDocumentationProvider() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)
    private val ENTRY_SEP = "|||"

    /* ---------------------------------------------------------------------- */
    /*  generateDoc()                                                         */
    /* ---------------------------------------------------------------------- */
    override fun generateDoc(element: PsiElement, original: PsiElement?): String? {
        try {
            val project = element.project
            if (DumbService.isDumb(project)) return null
            ProgressManager.checkCanceled()

            val settings = CssVarsAssistantSettings.getInstance()
            val varName = extractVariableName(element) ?: return null
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            // ------------------- hent alle verdier for variabelen -------------
            val rawEntries = FileBasedIndex.getInstance()
                .getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
                .flatMap { it.split(ENTRY_SEP) }
                .filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            // (ctx, resolved-info, kommentar)
            val parsed = rawEntries.mapNotNull {
                val parts = it.split(DELIMITER, limit = 3)
                if (parts.size >= 2) {
                    val ctx = parts[0]
                    val resInfo = resolveVarValue(project, parts[1])
                    val comment = parts.getOrElse(2) { "" }
                    Triple(ctx, resInfo, comment)
                } else null
            }

            // collapse «last wins» pr. context
            val collapsed = parsed
                .groupBy { it.first }
                .mapValues { (_, list) -> list.last() }
                .values.toList()

            val sorted = collapsed.sortedWith(
                compareBy(
                    { rank(it.first).first },
                    { rank(it.first).second ?: Int.MAX_VALUE },
                    { rank(it.first).third }
                )
            )

            /* ---------- Doc-parser til heading/description ------------------ */
            val docEntry = collapsed.firstOrNull { it.third.isNotBlank() }
                ?: collapsed.find { it.first == "default" }
                ?: collapsed.first()
            val doc = DocParser.parse(docEntry.third, docEntry.second.resolved)

            /* ---------- Skal vi vise Pixel-kolonne? ------------------------- */
            val showPixelCol = sorted.any { (_, res, _) ->
                if (!ValueUtil.isSizeValue(res.resolved)) return@any false
                val unit  = res.resolved.replace(Regex("[0-9.+\\-]"), "")
                    .trim().lowercase()
                val pxVal = ValueUtil.convertToPixels(res.resolved)
                val numericRaw = res.resolved
                    .replace(Regex("[^0-9.+\\-]"), "")
                    .toDoubleOrNull() ?: pxVal
                unit != "px" || pxVal.roundToInt() != numericRaw.roundToInt()
            }

            /* ---------- HTML-builder --------------------------------------- */
            val sb = StringBuilder()
            sb.append("<html><head><style>")
                .append("body { font-size: 11px; line-height: 1.3; }")
                .append("table { font-size: 10px; border-collapse: collapse; }")
                .append("td { padding: 2px 4px; max-width: 220px; word-wrap: break-word; }")
                .append("code { font-size: 10px; }")
                .append("</style></head><body>")
                .append(DocumentationMarkup.DEFINITION_START)

            if (doc.name.isNotBlank()) {
                sb.append("<b>").append(StringUtil.escapeXmlEntities(doc.name)).append("</b><br/>")
            }
            sb.append("<small>CSS Variable: <code>")
                .append(StringUtil.escapeXmlEntities(varName))
                .append("</code></small>")
                .append(DocumentationMarkup.DEFINITION_END)
                .append(DocumentationMarkup.CONTENT_START)

            /* ----------------------- TABELLen ------------------------------ */
            sb.append("<p><b>Values:</b></p><table>")
                .append("<tr><td>Context</td>")
                .append("<td>&nbsp;</td>")            // farge-swatch
                .append("<td>Value</td>")
                .append("<td>Type</td>")
                .append("<td>Source</td>")
                .append("<td>Uses</td>")
                .append("<td>WCAG</td>")
            if (showPixelCol) sb.append("<td>Pixel&nbsp;Eq.</td>")
            sb.append("</tr>")

            for ((ctx, resInfo, _) in sorted) {
                ProgressManager.checkCanceled()

                val value      = resInfo.resolved
                val isColour   = ColorParser.parseCssColor(value) != null
                val pixelEq    = if (ValueUtil.isSizeValue(value))
                    "${ValueUtil.convertToPixels(value).roundToInt()}px" else "—"
                val typeStr    = ValueUtil.getValueType(value).name
                val sourceStr  = if (resInfo.steps.isNotEmpty()) resInfo.steps.first() else "—"


                val usagePair = getUsageStats(project, varName)
                val usageTxt  = "${usagePair.second}×"

                val luminance = ColorParser.parseCssColor(value)?.let { c ->
                    val lr = listOf(c.red, c.green, c.blue).map { ch ->
                        val v = ch / 255.0
                        if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055)/1.055, 2.4)
                    }
                    0.2126*lr[0] + 0.7152*lr[1] + 0.0722*lr[2]
                }
                val contrastTxt = luminance?.let { "%.2f:1".format((1.05)/(it+0.05)) } ?: "—"


                sb.append("<tr>")

                /* -- Context ------------------------------------------------ */
                sb.append("<td style='color:#888;padding-right:10px'>")
                    .append(StringUtil.escapeXmlEntities(contextLabel(ctx, isColour)))
                    .append("</td>")

                /* -- Farge‐swatch ------------------------------------------- */
                sb.append("<td>")
                if (isColour) sb.append(colorSwatchHtml(value)) else sb.append("&nbsp;")
                sb.append("</td>")

                /* -- Value (m/ tooltip hvis avledet) ------------------------ */
                sb.append("<td>")
                if (resInfo.steps.isNotEmpty() && resInfo.original != resInfo.resolved) {
                    sb.append(StringUtil.escapeXmlEntities(resInfo.resolved))
                        .append(" <span title='")
                        .append(StringUtil.escapeXmlEntities(resInfo.steps.joinToString(" → ")))
                        .append("' style='color:#888;font-size:0.8em'>↪</span>")
                } else {
                    sb.append(StringUtil.escapeXmlEntities(value))
                }
                sb.append("</td>")

                /* -- Type --------------------------------------------------- */
                sb.append("<td style='color:#666'>").append(typeStr).append("</td>")

                /* -- Source ------------------------------------------------- */
                sb.append("<td style='color:#666'>")
                    .append(StringUtil.escapeXmlEntities(sourceStr))
                    .append("</td>")

                /* -- Pixel col ---------------------------------------------- */
                if (showPixelCol) {
                    sb.append("<td style='color:#666;font-size:0.8em'>")
                        .append(pixelEq)
                        .append("</td>")
                } else {
                    sb.append("<td>&nbsp;</td>")
                }



                sb.append("</tr>")
            }
            sb.append("</table>")

            /* ---------------- Optional blocks (slått av) ------------------- */
            // Vi dropper Usage/Dependencies/Related/Files fordi tallene er upålitelige.
            // La innstillingene stå hvis du vil re-aktivere senere.

            /* ---------------- Description / Examples ------------------------ */
            if (doc.description.isNotBlank()) {
                sb.append("<p><b>Description:</b><br/>")
                    .append(StringUtil.escapeXmlEntities(doc.description)).append("</p>")
            }

            if (doc.examples.isNotEmpty()) {
                sb.append("<p><b>Examples:</b></p><pre>")
                doc.examples.forEach { sb.append(StringUtil.escapeXmlEntities(it)).append('\n') }
                sb.append("</pre>")
            }

            /* -------------- Quick link til kontrast-tester for farger -------- */
            sorted.mapNotNull { ColorParser.parseCssColor(it.second.resolved) }
                .firstOrNull()?.let { c ->
                    val hex = "%02x%02x%02x".format(c.red, c.green, c.blue)
                    sb.append(
                        """<p style='margin-top:10px'>
                               <a target="_blank"
                                  href="https://webaim.org/resources/contrastchecker/?fcolor=$hex&bcolor=000000">
                                  Check contrast on WebAIM Contrast Checker
                               </a></p>"""
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

        if (Regex("\\bprint\\b", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) {
            return "Print"
        }

        if (Regex("only\\s+screen", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) {
            return "Only screen"
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
        val cache = DocStatsCache.get(project)
        return cache.usage(varName) {
            val settings = CssVarsAssistantSettings.getInstance()
            val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            val files = FileBasedIndex.getInstance()
                .getContainingFiles(CSS_VARIABLE_INDEXER_NAME, varName, scope)

            val totalUsages = files.sumOf { file ->
                try {
                    val content = file.inputStream.bufferedReader().readText()
                    Regex("""var\(\s*${Regex.escape(varName)}\s*\)""").findAll(content).count()
                } catch (e: Exception) {
                    0
                }
            }

            files.size to totalUsages
        }
    }

    private fun findDependencies(project: Project, varName: String, visited: Set<String> = emptySet()): List<String> {
        if (varName in visited) return emptyList()

        val cache = DocStatsCache.get(project)
        return cache.deps(varName) {
            val settings = CssVarsAssistantSettings.getInstance()
            val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            val entries = FileBasedIndex.getInstance()
                .getValues(CSS_VARIABLE_INDEXER_NAME, varName, scope)
                .flatMap { it.split(ENTRY_SEP) }
                .filter { it.isNotBlank() }

            entries.flatMap { entry ->
                val value = entry.split(DELIMITER).getOrNull(1) ?: ""
                Regex("""var\(\s*(--[\w-]+)\s*\)""").findAll(value)
                    .map { it.groupValues[1] }
                    .flatMap { ref -> listOf(ref) + findDependencies(project, ref, visited + varName) }
            }.distinct()
        }
    }

    private fun findRelatedVariables(project: Project, varName: String): List<String> {
        val cache = DocStatsCache.get(project)
        return cache.related(varName) {
            val keyCache = CssVarKeyCache.get(project)
            val base = varName.removePrefix("--")

            keyCache.getKeys()
                .filter { it != varName }
                .filter { candidate ->
                    val candidateBase = candidate.removePrefix("--")
                    base.split("-").any { part ->
                        part.length > 2 && candidateBase.contains(part, ignoreCase = true)
                    }
                }
                .take(8)
        }
    }

    private fun getFileLocations(project: Project, varName: String): List<String> {
        val cache = DocStatsCache.get(project)
        return cache.files(varName) {
            val settings = CssVarsAssistantSettings.getInstance()
            val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            FileBasedIndex.getInstance()
                .getContainingFiles(CSS_VARIABLE_INDEXER_NAME, varName, scope)
                .map { it.name }
                .distinct()
                .take(5)
        }
    }
}