package cssvarsassistant.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CssVariableIndex
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.CssVarDoc
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings

class CssVariableDocumentation : AbstractDocumentationProvider() {

    private val LOG = Logger.getInstance(CssVariableDocumentation::class.java)
    private val ENTRY_SEP = "|||"
    private val LESS_VAR_PATTERN = Regex("""^@([\w-]+)$""")

    override fun generateDoc(element: PsiElement, original: PsiElement?): String? {
        try {
            val varName = extractVariableName(element) ?: return null
            val settings = CssVarsAssistantSettings.getInstance()

            // Check if hover documentation is disabled
            if (!settings.enableHoverDocumentation) {
                return null
            }

            val project = element.project
            val scope = when (settings.indexingScope) {
                CssVarsAssistantSettings.IndexingScope.GLOBAL -> GlobalSearchScope.allScope(project)
                else -> GlobalSearchScope.projectScope(project)
            }

            val rawEntries = FileBasedIndex.getInstance().getValues(CssVariableIndex.NAME, varName, scope)
                .flatMap { it.split(ENTRY_SEP) }.filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            val parsed: List<Triple<String, String, String>> = rawEntries.mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) {
                    val ctx = p[0]
                    val value = if (settings.enableAliasResolution) {
                        resolveVarValue(project, p[1], scope)
                    } else {
                        p[1]
                    }
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

            return generateHtmlDocumentation(varName, doc, sorted, settings)

        } catch (e: Exception) {
            LOG.error("Error generating documentation", e)
            return null
        }
    }

    private fun generateHtmlDocumentation(
        varName: String,
        doc: CssVarDoc,
        sorted: List<Triple<String, String, String>>,
        settings: CssVarsAssistantSettings
    ): String {
        val sb = StringBuilder()

        // Header
        sb.append("<html><body>")
            .append(DocumentationMarkup.DEFINITION_START)

        if (doc.name.isNotBlank()) {
            sb.append("<b>").append(StringUtil.escapeXmlEntities(doc.name)).append("</b><br/>")
        }

        sb.append("<small>CSS Variable: <code>$varName</code></small>")
            .append(DocumentationMarkup.DEFINITION_END)
            .append(DocumentationMarkup.CONTENT_START)

        // Values table
        sb.append("<p><b>Values:</b></p>")
            .append("<table style='border-collapse: collapse;'>")
            .append("<tr><td style='padding: 2px 8px; font-weight: bold;'>Context</td>")
            .append("<td style='padding: 2px 8px; font-weight: bold;'>&nbsp;</td>")
            .append("<td style='padding: 2px 8px; font-weight: bold;' align='left'>Value</td></tr>")

        for ((ctx, value, _) in sorted) {
            val isColour = settings.enableColorPreview && ColorParser.parseCssColor(value) != null
            sb.append("<tr>")
                .append("<td style='color:#888; padding: 2px 8px; border-top: 1px solid #eee;'>")
                .append(StringUtil.escapeXmlEntities(contextLabel(ctx)))
                .append("</td>")
                .append("<td style='padding: 2px 8px; border-top: 1px solid #eee;'>")

            if (isColour) {
                sb.append(colorSwatchHtml(value))
            } else {
                sb.append("&nbsp;")
            }

            sb.append("</td>")
                .append("<td style='padding: 2px 8px; border-top: 1px solid #eee;'>")
                .append(StringUtil.escapeXmlEntities(value))
                .append("</td></tr>")
        }
        sb.append("</table>")

        // Description
        if (doc.description.isNotBlank()) {
            sb.append("<p><b>Description:</b><br/>")
                .append(StringUtil.escapeXmlEntities(doc.description))
                .append("</p>")
        }

        // Examples
        if (doc.examples.isNotEmpty()) {
            sb.append("<p><b>Examples:</b></p><pre style='background: #f5f5f5; padding: 8px; border-radius: 4px;'>")
            doc.examples.forEach {
                sb.append(StringUtil.escapeXmlEntities(it)).append('\n')
            }
            sb.append("</pre>")
        }

        // WebAIM link (if enabled and color found)
        if (settings.showWebAimLinks) {
            sorted.mapNotNull { ColorParser.parseCssColor(it.second) }.firstOrNull()?.let { c ->
                val hex = "%02x%02x%02x".format(c.red, c.green, c.blue)
                sb.append(
                    """<p style='margin-top:10px; padding: 8px; background: #f0f8ff; border-radius: 4px;'>
                         |<b>Accessibility:</b><br/>
                         |<a target="_blank" style='color: #0066cc;'
                         |   href="https://webaim.org/resources/contrastchecker/?fcolor=$hex&bcolor=000000">
                         |🔍 Check contrast on WebAIM Contrast Checker
                         |</a></p>""".trimMargin()
                )
            }
        }

        sb.append(DocumentationMarkup.CONTENT_END).append("</body></html>")
        return sb.toString()
    }

    private fun resolveVarValue(
        project: Project,
        raw: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet(),
        depth: Int = 0
    ): String {
        if (depth > 5) return raw

        val varRef = Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)
        if (varRef != null) {
            val ref = varRef.groupValues[1]
            if (ref in visited) return raw

            val entries = FileBasedIndex.getInstance()
                .getValues(CssVariableIndex.NAME, ref, scope)
                .flatMap { it.split(ENTRY_SEP) }
                .filter { it.isNotBlank() }

            val defValue = entries.mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) p[0] to p[1] else null
            }.let { pairs ->
                pairs.find { it.first == "default" }?.second ?: pairs.firstOrNull()?.second
            } ?: return raw

            return resolveVarValue(project, defValue, scope, visited + ref, depth + 1)
        }

        val lessVarMatch = LESS_VAR_PATTERN.find(raw.trim())
        if (lessVarMatch != null && CssVarsAssistantSettings.getInstance().preprocessorVariableSupport) {
            val varName = lessVarMatch.groupValues[1]
            return findPreprocessorVariableValue(project, varName, scope) ?: raw
        }

        return raw
    }

    private fun findPreprocessorVariableValue(
        project: Project,
        varName: String,
        scope: GlobalSearchScope
    ): String? {
        try {
            val potentialFiles = mutableListOf<VirtualFile>()
            val fileTypes = listOf("less", "scss", "sass", "css")

            for (ext in fileTypes) {
                for (commonName in listOf("variables", "vars", "theme", "colors", "spacing", "tokens")) {
                    val files = FilenameIndex.getAllFilesByExt(project, ext, scope)
                    files.filter { it.nameWithoutExtension.startsWith(commonName) }
                        .forEach { potentialFiles.add(it) }
                }
            }

            for (file in potentialFiles) {
                try {
                    val content = String(file.contentsToByteArray())

                    // Try different variable syntaxes
                    val patterns = listOf(
                        Regex("""@${Regex.escape(varName)}:\s*([^;]+);"""),      // LESS
                        Regex("""\$${Regex.escape(varName)}:\s*([^;]+);"""),     // SCSS
                        Regex("""--${Regex.escape(varName)}:\s*([^;]+);""")      // CSS
                    )

                    for (pattern in patterns) {
                        pattern.find(content)?.let {
                            return it.groupValues[1].trim()
                        }
                    }
                } catch (e: Exception) {
                    LOG.debug("Error reading file ${file.path}", e)
                }
            }

            return null
        } catch (e: Exception) {
            LOG.error("Error finding preprocessor variable value", e)
            return null
        }
    }

    private fun contextLabel(ctx: String): String = when {
        ctx == "default" -> "Default"
        "prefers-color-scheme" in ctx.lowercase() && "light" in ctx.lowercase() -> "Light"
        "prefers-color-scheme" in ctx.lowercase() && "dark" in ctx.lowercase() -> "Dark"
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