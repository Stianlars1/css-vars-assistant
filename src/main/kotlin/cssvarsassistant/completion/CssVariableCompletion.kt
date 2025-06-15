package cssvarsassistant.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.CssFunction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.ui.ColorIcon
import cssvarsassistant.documentation.ColorParser
import cssvarsassistant.documentation.lastLocalValueInFile
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

private const val COMPLETION_LOOKUP_ELEMENT_PRIORITY_BASE = 10000

class CssVariableCompletion : CompletionContributor() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)
    private val ENTRY_SEP = "|||"

    data class Entry(
        val rawName: String,
        val display: String,
        val mainValue: String,
        val allValues: List<Pair<String, String>>,
        val doc: String,
        val isAllColor: Boolean,
        val derived: Boolean

    )

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
                    val startTime = System.currentTimeMillis()
                    try {
                        val project = params.position.project
                        if (DumbService.isDumb(project)) return
                        ProgressManager.checkCanceled()

                        if (!isInsideVarFunction(params)) return

                        val rawPref = extractVarPrefix(params)
                        val simplePrefix = rawPref.removePrefix("--")

                        val settings = CssVarsAssistantSettings.getInstance()
                        val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)
                        val keyCache = CssVarKeyCache.get(project)

                        val entries = mutableListOf<Entry>()

                        /* ---------------------------------------------------- */
                        /*  for hver variabel-key                               */
                        /* ---------------------------------------------------- */
                        keyCache.getKeys().forEach { rawName ->
                            ProgressManager.checkCanceled()

                            val display = rawName.removePrefix("--")
                            if (simplePrefix.isNotBlank() &&
                                !display.startsWith(simplePrefix, ignoreCase = true)
                            ) return@forEach

                            /* ---- hent alle values -------------------------- */
                            val allVals = FileBasedIndex.getInstance()
                                .getValues(CSS_VARIABLE_INDEXER_NAME, rawName, cssScope)
                                .flatMap { it.split(ENTRY_SEP) }
                                .distinct()
                                .filter { it.isNotBlank() }

                            if (allVals.isEmpty()) return@forEach

                            /* ---- map til (context, resolved) --------------- */
                            var didResolve = false

                            val valuePairs = allVals.mapNotNull {
                                val p = it.split(DELIMITER, limit = 3)
                                if (p.size >= 2) {
                                    val ctx = p[0]
                                    val rawVal = p[1]
                                    val resolved = resolveVarValue(project, cssScope, rawVal)
                                    if (resolved != rawVal) didResolve = true
                                    ctx to resolved
                                } else null
                            }


                            val uniquePairs = valuePairs
                                .asReversed()
                                .distinctBy { it.first to it.second }     // beholder én rad per (context,value)
                                .asReversed()

                            /* --- finn cascade-vinner --- */
                            val localOverride = lastLocalValueInFile(params.position.text, rawName)

                            val mainValue = localOverride
                                ?: uniquePairs.filter { it.first == "default" }.lastOrNull()?.second
                                ?: uniquePairs.last().second


                            val docEntry = allVals.firstOrNull {
                                it.substringAfter(DELIMITER)
                                    .substringAfter(DELIMITER)
                                    .isNotBlank()
                            } ?: allVals.first()
                            val commentTxt = docEntry.split(DELIMITER).getOrNull(2) ?: ""
                            val doc = DocParser.parse(commentTxt, mainValue).description

                            val values = uniquePairs.map { it.second }.distinct()
                            val isAllColor = values.all { ColorParser.parseCssColor(it) != null }



                            entries += Entry(
                                rawName,
                                display,
                                mainValue.trim(),
                                uniquePairs,
                                doc,
                                isAllColor,
                                didResolve
                            )
                        }

                        /* ------------- sortering -------------------------- */
                        entries.sortWith(createSmartComparator(settings.sortingOrder))

                        /* ------------- bygg Lookup-elementer -------------- */
                        entries.forEachIndexed { idx, e ->
                            ProgressManager.checkCanceled()
                            val priority = (COMPLETION_LOOKUP_ELEMENT_PRIORITY_BASE - idx).toDouble()

                            val shortDoc = e.doc.takeIf { it.isNotBlank() }
                                ?.let { it.take(40) + if (it.length > 40) "…" else "" } ?: ""

                            val colorIcons = e.allValues.mapNotNull { (_, v) ->
                                ColorParser.parseCssColor(v)?.let { ColorIcon(12, it, false) }
                            }.distinctBy { it.iconColor }

                            val icon: Icon = when {
                                e.isAllColor && colorIcons.size == 2 -> DoubleColorIcon(colorIcons[0], colorIcons[1])
                                e.isAllColor && colorIcons.isNotEmpty() -> colorIcons[0]
                                else -> AllIcons.FileTypes.Css
                            }

                            /* ---- valueText m/ ↗ hvis avledet -------------- */
                            val valueText = when {
                                e.isAllColor && e.allValues.size > 1 && settings.showContextValues ->
                                    e.allValues.joinToString(" / ") { (ctx, v) ->
                                        if ("dark" in ctx.lowercase()) "🌙 $v" else v
                                    }

                                e.isAllColor -> e.mainValue

                                else -> buildString {
                                    append(e.mainValue)
                                    if (settings.showContextValues && e.allValues.size > 1) {
                                        append(" (+${e.allValues.size - 1})")
                                    }
                                }
                            }.let { if (e.derived) "$it ↗" else it }


                            val element = LookupElementBuilder
                                .create(e.rawName)
                                .withPresentableText(e.display)
                                .withLookupString(e.display)
                                .withIcon(icon)
                                .withTypeText(valueText, true)
                                .withTailText(if (shortDoc.isNotBlank()) " — $shortDoc" else "", true)

                            result.addElement(
                                PrioritizedLookupElement
                                    .withPriority(element, priority)
                            )
                        }

                        result.stopHere()
                        logger.info(
                            "CSS var completion: ${System.currentTimeMillis() - startTime}ms, " +
                                    "${entries.size} entries."
                        )

                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("CSS var completion error", e)
                    }
                }
            }
        )
    }


    private fun resolveVarValue(
        project: Project,
        scope: com.intellij.psi.search.GlobalSearchScope,
        raw: String,
        visited: Set<String> = emptySet(),
        depth: Int = 0
    ): String {
        val settings = CssVarsAssistantSettings.getInstance()
        if (depth > settings.maxImportDepth) return raw

        try {
            ProgressManager.checkCanceled()

            /* var(--other) -------------------------------------------------- */
            Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)?.let { m ->
                val ref = m.groupValues[1]
                if (ref in visited) return raw

                val entries = FileBasedIndex.getInstance()
                    .getValues(CSS_VARIABLE_INDEXER_NAME, ref, scope)
                    .flatMap { it.split(ENTRY_SEP) }
                    .filter { it.isNotBlank() }

                val defVal = entries.mapNotNull {
                    val p = it.split(DELIMITER, limit = 3)
                    if (p.size >= 2) p[0] to p[1] else null
                }.let { list ->
                    list.find { it.first == "default" }?.second ?: list.firstOrNull()?.second
                }

                if (defVal != null)
                    return resolveVarValue(project, scope, defVal, visited + ref, depth + 1)
                return raw
            }

            /* @less / $scss ----------------------------------------------- */
            Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())?.let { m ->
                val varName = m.groupValues[1]
                CssVarCompletionCache.get(project, varName)?.let { return it }

                val resolved = findPreprocessorVariableValue(project, varName)
                if (resolved != null) CssVarCompletionCache.put(project, varName, resolved)
                return resolved ?: raw
            }

            return raw

        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to resolve var value: $raw", e)
            return raw
        }
    }

    private fun createSmartComparator(order: CssVarsAssistantSettings.SortingOrder): Comparator<Entry> {
        val baseComparator = Comparator<Entry> { a, b ->
            // 1. Group by value type
            val aType = ValueUtil.getValueType(a.mainValue)
            val bType = ValueUtil.getValueType(b.mainValue)

            if (aType != bType) {
                return@Comparator aType.ordinal - bType.ordinal
            }

            // 2. Sort within same type
            when (aType) {
                ValueUtil.ValueType.SIZE -> ValueUtil.compareSizes(a.mainValue, b.mainValue)
                ValueUtil.ValueType.COLOR -> ValueUtil.compareColors(a.mainValue, b.mainValue)
                ValueUtil.ValueType.NUMBER -> ValueUtil.compareNumbers(a.mainValue, b.mainValue)
                ValueUtil.ValueType.OTHER -> a.display.compareTo(b.display, true)
            }
        }

        return if (order == CssVarsAssistantSettings.SortingOrder.DESC) {
            baseComparator.reversed()
        } else {
            baseComparator
        }
    }

    private fun isInsideVarFunction(params: CompletionParameters): Boolean {
        val offset = params.offset
        val document = params.editor.document
        val text = document.text

        try {
            val lineNumber = document.getLineNumber(offset)
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val lineText = text.substring(lineStart, lineEnd)
            val positionInLine = offset - lineStart

            logger.debug("Line analysis: '$lineText' at position $positionInLine")

            val varPattern = Regex("""var\s*\(""")
            val matches = varPattern.findAll(lineText).toList()

            for (match in matches) {
                val varOpenParenPos = match.range.last

                if (positionInLine > varOpenParenPos) {
                    val remainingText = lineText.substring(positionInLine)
                    val closingParenIndex = remainingText.indexOf(')')

                    if (closingParenIndex == -1) {
                        logger.debug("✅ Found var( without closing paren")
                        return true
                    } else {
                        logger.debug("✅ Found var( with closing paren at ${positionInLine + closingParenIndex}")
                        return true
                    }
                }
            }

            val searchStart = maxOf(0, offset - 100)
            val searchEnd = minOf(text.length, offset + 20)
            val searchText = text.substring(searchStart, searchEnd)
            val cursorInSearch = offset - searchStart

            logger.debug("Broader search: '${searchText.replace('\n', '↵')}' cursor at $cursorInSearch")

            val nearbyMatches = varPattern.findAll(searchText).toList()
            for (match in nearbyMatches) {
                val varOpenParenPos = match.range.last

                if (cursorInSearch > varOpenParenPos) {
                    val afterVarText = searchText.substring(varOpenParenPos + 1)
                    val closingParenIndex = afterVarText.indexOf(')')

                    if (closingParenIndex == -1 || cursorInSearch <= varOpenParenPos + 1 + closingParenIndex) {
                        logger.debug("✅ Found var( in broader search")
                        return true
                    }
                }
            }

            val pos = params.position
            val fn = PsiTreeUtil.getParentOfType(pos, CssFunction::class.java)
            if (fn?.name == "var") {
                val l = fn.lParenthesis?.textOffset
                val r = fn.rParenthesis?.textOffset
                if (l != null && (r == null || (offset > l && offset <= r))) {
                    logger.debug("✅ PSI detection success")
                    return true
                }
            }

            logger.debug("❌ No var() context detected")
            return false

        } catch (e: Exception) {
            logger.debug("Error in var detection: ${e.message}")
            return false
        }
    }

    private fun extractVarPrefix(params: CompletionParameters): String {
        val offset = params.offset
        val document = params.editor.document
        val text = document.text

        try {
            val searchStart = maxOf(0, offset - 200)
            val searchText = text.substring(searchStart, offset)

            val varPattern = Regex("""var\s*\(""")
            val matches = varPattern.findAll(searchText).toList()

            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val varStart = searchStart + lastMatch.range.last + 1
                val prefix = text.substring(varStart, offset).trim()
                logger.debug("Extracted prefix: '$prefix'")
                return prefix
            }

            return ""
        } catch (e: Exception) {
            logger.debug("Error extracting prefix: ${e.message}")
            return ""
        }
    }


    private fun findPreprocessorVariableValue(project: Project, varName: String): String? {
        return try {
            val freshScope = ScopeUtil.currentPreprocessorScope(project)
            PreprocessorUtil.resolveVariable(project, varName, freshScope)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to find preprocessor variable: $varName", e)
            null
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

private fun lastLocalValue(params: CompletionParameters, varName: String): String? {
    val text = params.originalFile.text
    val regex = Regex("""\Q$varName\E\s*:\s*([^;]+);""")
    return regex.findAll(text)
        .map { it.groupValues[1].trim() }
        .lastOrNull()
}