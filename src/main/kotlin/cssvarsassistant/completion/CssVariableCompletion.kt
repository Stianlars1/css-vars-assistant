package cssvarsassistant.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.CssFunction
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.ui.ColorIcon
import cssvarsassistant.documentation.ColorParser
import cssvarsassistant.index.CssVariableIndex
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class CssVariableCompletion : CompletionContributor() {
    private val LOG = Logger.getInstance(CssVariableCompletion::class.java)
    private val ENTRY_SEP = "|||"
    private val LESS_VAR_PATTERN = Regex("""^@([\w-]+)$""")

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    params: CompletionParameters,
                    ctx: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    try {
                        // Only inside var(...) args
                        val pos = params.position
                        val fn = PsiTreeUtil.getParentOfType(pos, CssFunction::class.java) ?: return
                        if (fn.name != "var") return
                        val l = fn.lParenthesis?.textOffset ?: return
                        val r = fn.rParenthesis?.textOffset ?: return
                        val off = params.offset
                        if (off <= l || off > r) return

                        val rawPref = result.prefixMatcher.prefix
                        val simple = rawPref.removePrefix("--")
                        val project = pos.project
                        val settings = CssVarsAssistantSettings.getInstance()

                        val scope = when (settings.indexingScope) {
                            CssVarsAssistantSettings.IndexingScope.GLOBAL -> GlobalSearchScope.allScope(project)
                            else -> GlobalSearchScope.projectScope(project)
                        }

                        val processedVariables = mutableSetOf<String>()
                        val entries = mutableListOf<CompletionEntry>()

                        var processedCount = 0
                        FileBasedIndex.getInstance()
                            .getAllKeys(CssVariableIndex.NAME, project)
                            .forEach { rawName ->
                                if (processedCount >= settings.maxCompletionItems) {
                                    return@forEach  // Stop processing when limit reached
                                }

                                val display = rawName.removePrefix("--")
                                if (!display.startsWith(simple, ignoreCase = true)) return@forEach

                                processedVariables.add(rawName)

                                val entry = createCompletionEntry(rawName, display, project, scope, settings)
                                if (entry != null) {
                                    entries.add(entry)
                                    processedCount++
                                }
                            }

                        // Sort entries for better UX
                        entries.sortBy { it.display }

                        // Add completion elements
                        for (entry in entries) {
                            val lookupElement = createLookupElement(entry, settings)
                            result.addElement(lookupElement)
                        }

                        // Handle IDE completions fallback
                        handleIdeCompletionsFallback(settings, processedVariables, result, rawPref)

                    } catch (ex: Exception) {
                        LOG.error("CSS var completion error", ex)
                    }
                }
            }
        )
    }

    private data class CompletionEntry(
        val rawName: String,
        val display: String,
        val mainValue: String,
        val allValues: List<Pair<String, String>>,
        val doc: String,
        val isAllColor: Boolean,
        val colorIcons: List<ColorIcon>
    )

    private fun createCompletionEntry(
        rawName: String,
        display: String,
        project: Project,
        scope: GlobalSearchScope,
        settings: CssVarsAssistantSettings
    ): CompletionEntry? {
        val allVals = FileBasedIndex.getInstance()
            .getValues(CssVariableIndex.NAME, rawName, scope)
            .flatMap { it.split(ENTRY_SEP) }
            .distinct()
            .filter { it.isNotBlank() }

        if (allVals.isEmpty()) return null

        val valuePairs = allVals.mapNotNull {
            val parts = it.split(DELIMITER, limit = 3)
            if (parts.size >= 2) {
                val ctx = parts[0]
                val rawVal = parts[1]
                val resolved = if (settings.enableAliasResolution) {
                    resolveVarValue(project, rawVal, scope)
                } else {
                    rawVal
                }
                ctx to resolved
            } else null
        }

        val uniqueValuePairs: List<Pair<String, String>> = valuePairs.distinctBy { (ctx, v) -> ctx to v }
        val values = uniqueValuePairs.map { it.second }.distinct()
        val mainValue = uniqueValuePairs.find { it.first == "default" }?.second ?: values.first()

        val docEntry = allVals.firstOrNull { it.split(DELIMITER, limit = 3).getOrNull(2)?.isNotBlank() == true }
            ?: allVals.first()
        val commentTxt = docEntry.split(DELIMITER, limit = 3).getOrNull(2) ?: ""
        val doc = DocParser.parse(commentTxt, mainValue).description

        val isAllColor = settings.enableColorPreview &&
                values.isNotEmpty() && values.all { ColorParser.parseCssColor(it) != null }

        val colorIcons = if (isAllColor) {
            uniqueValuePairs.mapNotNull { (_, v) ->
                ColorParser.parseCssColor(v)?.let { ColorIcon(12, it, false) }
            }.distinctBy { it.iconColor }
        } else {
            emptyList()
        }

        return CompletionEntry(rawName, display, mainValue, uniqueValuePairs, doc, isAllColor, colorIcons)
    }

    private fun createLookupElement(entry: CompletionEntry, settings: CssVarsAssistantSettings): LookupElementBuilder {
        val short = entry.doc.takeIf { it.isNotBlank() }
            ?.let { it.take(40) + if (it.length > 40) "…" else "" }
            ?: ""

        val icon: Icon = when {
            entry.isAllColor && settings.enableColorPreview && entry.colorIcons.size >= 2 -> DoubleColorIcon(entry.colorIcons[0], entry.colorIcons[1])
            entry.isAllColor && settings.enableColorPreview && entry.colorIcons.isNotEmpty() -> entry.colorIcons[0]
            isSizeValue(entry.mainValue) -> AllIcons.FileTypes.Css
            else -> AllIcons.Nodes.Property
        }

        val valueText = when {
            entry.isAllColor && entry.allValues.size > 1 && settings.showContextValues -> {
                entry.allValues.joinToString(" / ") { (ctx, v) ->
                    when {
                        "dark" in ctx.lowercase() -> "\uD83C\uDF19 $v"
                        else -> v
                    }
                }
            }
            entry.isAllColor -> entry.mainValue
            entry.allValues.size > 1 && settings.showContextValues -> {
                "${entry.mainValue} (+${entry.allValues.size - 1})"
            }
            else -> entry.mainValue
        }

        return LookupElementBuilder
            .create(entry.rawName)
            .withPresentableText(entry.display)
            .withLookupString(entry.display)
            .withIcon(icon)
            .withTypeText(valueText, true)
            .withTailText(if (short.isNotBlank()) " — $short" else "", true)
            .withInsertHandler { ctx2, _ ->
                ctx2.document.replaceString(ctx2.startOffset, ctx2.tailOffset, entry.rawName)
            }
    }

    private fun resolveVarValue(
        project: Project,
        raw: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet(),
        depth: Int = 0
    ): String {
        if (!CssVarsAssistantSettings.getInstance().enableAliasResolution) return raw
        if (depth > 5) return raw

        // Handle var(--xyz) references
        val varRef = Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)
        if (varRef != null) {
            val ref = varRef.groupValues[1]
            if (ref in visited) return raw

            val refEntries = FileBasedIndex.getInstance()
                .getValues(CssVariableIndex.NAME, ref, scope)
                .flatMap { it.split(ENTRY_SEP) }
                .distinct()
                .filter { it.isNotBlank() }

            val refDefault = refEntries
                .mapNotNull {
                    val p = it.split(DELIMITER, limit = 3)
                    if (p.size >= 2) p[0] to p[1] else null
                }
                .let { pairs ->
                    pairs.find { it.first == "default" }?.second ?: pairs.firstOrNull()?.second
                }

            if (refDefault != null) {
                return resolveVarValue(project, refDefault, scope, visited + ref, depth + 1)
            }
        }

        // Handle preprocessor variable references (LESS/SCSS)
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
                        Regex("""\\${Regex.escape(varName)}:\s*([^;]+);"""),     // SCSS
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
            LOG.error("Error finding preprocessor variable '$varName'", e)
            return null
        }
    }

    private fun handleIdeCompletionsFallback(
        settings: CssVarsAssistantSettings,
        processedVariables: Set<String>,
        result: CompletionResultSet,
        rawPref: String
    ) {
        if (settings.allowIdeCompletions) {
            if (processedVariables.isNotEmpty()) {
                val filteredResult = result.withPrefixMatcher(object : PrefixMatcher(rawPref) {
                    override fun prefixMatches(name: String): Boolean {
                        return processedVariables.contains(name)
                    }

                    override fun cloneWithPrefix(prefix: String): PrefixMatcher {
                        return this
                    }
                })
                filteredResult.stopHere()
            }
        } else {
            result.stopHere()
        }
    }

    private fun isSizeValue(raw: String): Boolean {
        return Regex("""^-?\d+(\.\d+)?(px|em|rem|ch|ex|vh|vw|vmin|vmax|%)$""", RegexOption.IGNORE_CASE)
            .matches(raw.trim())
    }
}

class DoubleColorIcon(private val icon1: Icon, private val icon2: Icon) : Icon {
    override fun getIconWidth() = icon1.iconWidth + icon2.iconWidth + 2
    override fun getIconHeight() = maxOf(icon1.iconHeight, icon2.iconHeight)
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        icon1.paintIcon(c, g, x, y)
        icon2.paintIcon(c, g, x + icon1.iconWidth + 2, y)
    }
}