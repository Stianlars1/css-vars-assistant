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
    private val lessVarCache = mutableMapOf<Pair<Project, String>, String?>()

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
                        fun resolveVarValue(raw: String, visited: Set<String> = emptySet(), depth: Int = 0): String {
                            if (depth > 5) return raw

                            // Handle var(--xyz) references (existing logic)
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
                                        pairs.find { it.first == "default" }?.second
                                            ?: pairs.firstOrNull()?.second
                                    }

                                if (refDefault != null)
                                    return resolveVarValue(refDefault, visited + ref, depth + 1)
                                else
                                    return raw
                            }

                            // Enhanced LESS variable resolution
                            val lessVarMatch = LESS_VAR_PATTERN.find(raw.trim())
                            if (lessVarMatch != null) {
                                val varName = lessVarMatch.groupValues[1]
                                val cacheKey = Pair(project, varName)

                                // Check cache first
                                lessVarCache[cacheKey]?.let { cachedValue ->
                                    // Recursively resolve if the cached value is also a LESS variable
                                    return if (cachedValue.trim().startsWith("@")) {
                                        resolveVarValue(cachedValue, visited + raw.trim(), depth + 1)
                                    } else {
                                        cachedValue
                                    }
                                }

                                // First try to find in files that were already indexed
                                val resolvedValue = findLessVariableInIndexedFiles(project, varName, scope)
                                    ?: findPreprocessorVariableValue(project, varName, scope)

                                lessVarCache[cacheKey] = resolvedValue

                                if (resolvedValue != null) {
                                    // Recursively resolve if the resolved value is also a LESS variable
                                    return if (resolvedValue.trim().startsWith("@")) {
                                        resolveVarValue(resolvedValue, visited + raw.trim(), depth + 1)
                                    } else {
                                        resolvedValue
                                    }
                                }
                            }

                            return raw
                        }


                        data class Entry(
                            val rawName: String,
                            val display: String,
                            val mainValue: String,
                            val allValues: List<Pair<String, String>>,
                            val doc: String,
                            val isAllColor: Boolean
                        )

                        val entries = mutableListOf<Entry>()
                        FileBasedIndex.getInstance()
                            .getAllKeys(CssVariableIndex.NAME, project)
                            .forEach { rawName ->
                                val display = rawName.removePrefix("--")
                                if (!display.startsWith(simple, ignoreCase = true)) return@forEach

                                processedVariables.add(rawName)

                                val allVals = FileBasedIndex.getInstance()
                                    .getValues(CssVariableIndex.NAME, rawName, scope)
                                    .flatMap { it.split(ENTRY_SEP) }
                                    .distinct()
                                    .filter { it.isNotBlank() }

                                if (allVals.isEmpty()) return@forEach

                                val valuePairs = allVals.mapNotNull {
                                    val parts = it.split(DELIMITER, limit = 3)
                                    if (parts.size >= 2) {
                                        val ctx = parts[0]
                                        val rawVal = parts[1]
                                        val resolved = resolveVarValue(rawVal)
                                        ctx to resolved
                                    } else null
                                }


                                val uniqueValuePairs: List<Pair<String, String>> =
                                    valuePairs.distinctBy { (ctx, v) -> ctx to v }
                                val values = uniqueValuePairs.map { it.second }.distinct()
                                val mainValue = uniqueValuePairs.find { it.first == "default" }?.second
                                    ?: values.first()

                                val docEntry = allVals.firstOrNull { it.substringAfter(DELIMITER).isNotBlank() }
                                    ?: allVals.first()
                                val commentTxt = docEntry.substringAfter(DELIMITER)
                                val doc = DocParser.parse(commentTxt, mainValue).description

                                val isAllColor =
                                    values.isNotEmpty() && values.all { ColorParser.parseCssColor(it) != null }

                                entries += Entry(
                                    rawName,
                                    display,
                                    mainValue,
                                    uniqueValuePairs,
                                    doc,
                                    isAllColor
                                )
                            }

                        entries.sortBy { it.display }

                        for (e in entries) {
                            val short = e.doc.takeIf { it.isNotBlank() }
                                ?.let { it.take(40) + if (it.length > 40) "…" else "" }
                                ?: ""

                            if (e.rawName == "--ffe-farge-vann") {
                                debugLessVariableResolution(e.rawName, project, scope)
                            }

                            val colorIcons = e.allValues.mapNotNull { (_, v) ->
                                ColorParser.parseCssColor(v)?.let { ColorIcon(12, it, false) }
                            }.distinctBy { it.iconColor }

                            val icon: Icon = when {
                                e.isAllColor && colorIcons.size == 2 -> DoubleColorIcon(colorIcons[0], colorIcons[1])
                                e.isAllColor && colorIcons.isNotEmpty() -> colorIcons[0]
                                isSizeValue(e.mainValue) -> AllIcons.FileTypes.Css
                                else -> AllIcons.Nodes.Property
                            }

                            val valueText = when {
                                e.isAllColor && e.allValues.size > 1 && settings.showContextValues -> {
                                    e.allValues.joinToString(" / ") { (ctx, v) ->
                                        when {
                                            "dark" in ctx.lowercase() -> "\uD83C\uDF19 $v"
                                            else -> v
                                        }
                                    }
                                }

                                e.isAllColor -> e.mainValue
                                e.allValues.size > 1 && settings.showContextValues -> {
                                    "${e.mainValue} (+${e.allValues.size - 1})"
                                }

                                else -> e.mainValue
                            }

                            val elt = LookupElementBuilder
                                .create(e.rawName)
                                .withPresentableText(e.display)
                                .withLookupString(e.display)
                                .withIcon(icon)
                                .withTypeText(valueText, true)
                                .withTailText(if (short.isNotBlank()) " — $short" else "", true)
                                .withInsertHandler { ctx2, _ ->
                                    ctx2.document.replaceString(ctx2.startOffset, ctx2.tailOffset, e.rawName)
                                }

                            result.addElement(elt)
                        }

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
                    } catch (ex: Exception) {
                        LOG.error("CSS var completion error", ex)
                    }
                }
            }
        )
    }
// Enhanced findPreprocessorVariableValue function for CssVariableCompletion.kt
// This replaces the existing findPreprocessorVariableValue function

    private fun findPreprocessorVariableValue(
        project: Project,
        varName: String,
        scope: GlobalSearchScope
    ): String? {
        try {
            val potentialFiles = mutableListOf<VirtualFile>()
            val fileTypes = listOf("less", "scss", "sass", "css")

            // Collect potential files from the resolved imports
            for (ext in fileTypes) {
                for (commonName in listOf("variables", "vars", "theme", "colors", "spacing", "tokens", "color")) {
                    val files = FilenameIndex.getAllFilesByExt(project, ext, scope)
                    files.filter { it.name.startsWith(commonName) }
                        .forEach { psiFile -> potentialFiles.add(psiFile) }
                }
            }

            // Also search in recently indexed files (from imports)
            val allFiles = FilenameIndex.getAllFilesByExt(project, "less", scope) +
                    FilenameIndex.getAllFilesByExt(project, "scss", scope) +
                    FilenameIndex.getAllFilesByExt(project, "css", scope)

            potentialFiles.addAll(allFiles)

            // Remove duplicates and prioritize certain file patterns
            val uniqueFiles = potentialFiles.distinctBy { it.path }
                .sortedWith(compareBy<VirtualFile> { file ->
                    when {
                        file.name.contains("color") -> 0  // Colors first
                        file.name.contains("theme") -> 1  // Theme second
                        file.name.contains("variable") -> 2  // Variables third
                        else -> 3
                    }
                }.thenBy { it.name })

            LOG.debug("🔍 Searching for LESS variable @$varName in ${uniqueFiles.size} files")

            for (file in uniqueFiles) {
                try {
                    val content = String(file.contentsToByteArray())

                    // LESS variable pattern - look for @varName: value;
                    val lessPattern = Regex("""@${Regex.escape(varName)}:\s*([^;]+);""")
                    lessPattern.find(content)?.let { match ->
                        val value = match.groupValues[1].trim()
                        LOG.debug("✅ Found LESS variable @$varName = $value in ${file.name}")
                        return value
                    }

                    // SCSS variable pattern - look for $varName: value;
                    val scssPattern = Regex("""\$${Regex.escape(varName)}:\s*([^;]+);""")
                    scssPattern.find(content)?.let { match ->
                        val value = match.groupValues[1].trim()
                        LOG.debug("✅ Found SCSS variable \$$varName = $value in ${file.name}")
                        return value
                    }

                    // CSS custom property pattern - look for --varName: value;
                    val cssVarPattern = Regex("""--${Regex.escape(varName)}:\s*([^;]+);""")
                    cssVarPattern.find(content)?.let { match ->
                        val value = match.groupValues[1].trim()
                        LOG.debug("✅ Found CSS variable --$varName = $value in ${file.name}")
                        return value
                    }

                } catch (e: Exception) {
                    LOG.debug("⚠️ Error reading file ${file.path}: ${e.message}")
                }
            }

            LOG.debug("❌ LESS variable @$varName not found in any files")
            return null

        } catch (e: Exception) {
            LOG.error("💥 Error finding preprocessor variable value for @$varName", e)
            return null
        }
    }

    private fun isSizeValue(raw: String): Boolean {
        return Regex("""^-?\d+(\.\d+)?(px|em|rem|ch|ex|vh|vw|vmin|vmax|%)$""", RegexOption.IGNORE_CASE)
            .matches(raw.trim())
    }

    private fun findLessVariableInIndexedFiles(
        project: Project,
        varName: String,
        scope: GlobalSearchScope
    ): String? {
        try {
            // Get all keys from our index to see what files were processed
            val allCssVars = FileBasedIndex.getInstance().getAllKeys(CssVariableIndex.NAME, project)

            // Look for files that contain LESS variables in node_modules/@sb1/ffe-core
            val sb1Files = FilenameIndex.getAllFilesByExt(project, "less", scope)
                .filter { it.path.contains("@sb1/ffe-core") }

            LOG.debug("🔍 Searching for @$varName in ${sb1Files.size} SB1 LESS files")

            for (file in sb1Files) {
                try {
                    val content = String(file.contentsToByteArray())

                    // Look for @varName: value; patterns
                    val patterns = listOf(
                        Regex("""@${Regex.escape(varName)}:\s*([^;]+);"""),  // @var: value;
                        Regex("""@${Regex.escape(varName)}\s*:\s*([^;]+);"""), // @var : value;
                        Regex("""@${Regex.escape(varName)}:\s*([^;\n\r]+)""")   // @var: value (no semicolon)
                    )

                    for (pattern in patterns) {
                        pattern.find(content)?.let { match ->
                            val value = match.groupValues[1].trim()
                            LOG.info("✅ Found LESS variable @$varName = '$value' in ${file.name}")
                            return value
                        }
                    }

                } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    LOG.debug("⚠️ Error reading SB1 file ${file.path}: ${e.message}")
                }
            }

            LOG.debug("❌ LESS variable @$varName not found in SB1 files")
            return null

        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("💥 Error searching for LESS variable @$varName in indexed files", e)
            return null
        }
    }

    private fun debugLessVariableResolution(varName: String, project: Project, scope: GlobalSearchScope) {
        LOG.info("🔍 DEBUG: Starting resolution for CSS variable: $varName")

        // Check what's in our index for this variable
        val indexEntries = FileBasedIndex.getInstance()
            .getValues(CssVariableIndex.NAME, varName, scope)
            .flatMap { it.split(ENTRY_SEP) }
            .filter { it.isNotBlank() }

        LOG.info("📊 Index entries for $varName:")
        indexEntries.forEachIndexed { index, entry ->
            val parts = entry.split(DELIMITER, limit = 3)
            val context = parts.getOrElse(0) { "unknown" }
            val value = parts.getOrElse(1) { "unknown" }
            val comment = parts.getOrElse(2) { "" }
            LOG.info("  [$index] Context: '$context', Value: '$value', Comment: '$comment'")
        }

        // Check if the value contains a LESS variable reference
        val defaultValue = indexEntries
            .mapNotNull {
                val p = it.split(DELIMITER, limit = 3)
                if (p.size >= 2) p[0] to p[1] else null
            }
            .find { it.first == "default" }?.second
            ?: indexEntries.firstOrNull()?.split(DELIMITER, limit = 3)?.getOrNull(1)

        LOG.info("🎯 Default value for $varName: '$defaultValue'")

        if (defaultValue?.trim()?.startsWith("@") == true) {
            val lessVarName = defaultValue.trim().substring(1)
            LOG.info("🔗 This references LESS variable: @$lessVarName")

            // Try to find this LESS variable
            val sb1Files = FilenameIndex.getAllFilesByExt(project, "less", scope)
                .filter { it.path.contains("@sb1/ffe-core") }

            LOG.info("📁 Searching in ${sb1Files.size} SB1 LESS files")

            for (file in sb1Files.take(5)) { // Limit to first 5 for debug
                try {
                    val content = String(file.contentsToByteArray())
                    val lessPattern = Regex("""@${Regex.escape(lessVarName)}:\s*([^;]+);""")
                    val match = lessPattern.find(content)

                    if (match != null) {
                        LOG.info("✅ Found @$lessVarName = '${match.groupValues[1].trim()}' in ${file.name}")
                    } else {
                        // Show a snippet of the file content for debugging
                        val snippet = content.take(200).replace('\n', ' ')
                        LOG.info("❌ Not found in ${file.name} (snippet: $snippet)")
                    }
                } catch (e: Exception) {
                    LOG.info("⚠️ Error reading ${file.name}: ${e.message}")
                }
            }
        }
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















