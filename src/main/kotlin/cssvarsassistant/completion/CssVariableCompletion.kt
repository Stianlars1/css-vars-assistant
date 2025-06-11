package cssvarsassistant.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.CssFunction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import cssvarsassistant.documentation.ColorParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ScopeUtil
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class CssVariableCompletion : CompletionContributor() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

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

                        // ROBUST VAR() DETECTION: Multiple strategies to detect if we're inside var()
                        val varContext = detectVarContext(params)
                        if (!varContext.isInVar) {
                            logger.debug("Not in var() context: ${varContext.reason}")
                            return
                        }

                        logger.debug("CSS Variable completion triggered with strategy: ${varContext.strategy}")

                        val rawPref = result.prefixMatcher.prefix
                        val simple = rawPref.removePrefix("--")
                        val settings = CssVarsAssistantSettings.getInstance()

                        // Use stable cached scope
                        val cssScope = ScopeUtil.getStableCssIndexingScope(project, settings)

                        // Use pre-computed completion data
                        val completionData = CompletionDataCache.get(project).getCompletionEntries(cssScope)

                        ProgressManager.checkCanceled()

                        val processedVariables = mutableSetOf<String>()
                        var entriesAdded = 0

                        // Filter and add completion entries
                        for (entry in completionData) {
                            ProgressManager.checkCanceled()

                            // More flexible prefix matching
                            if (simple.isNotBlank() && !entry.display.startsWith(simple, ignoreCase = true)) {
                                continue
                            }

                            processedVariables.add(entry.rawName)

                            val short = entry.doc.takeIf { it.isNotBlank() }
                                ?.let { it.take(40) + if (it.length > 40) "…" else "" }
                                ?: ""

                            val colorIcons = entry.allValues.mapNotNull { (_, v) ->
                                ColorParser.parseCssColor(v)?.let { ColorIcon(12, it, false) }
                            }.distinctBy { it.iconColor }

                            val icon: Icon = when {
                                entry.isAllColor && colorIcons.size == 2 -> DoubleColorIcon(colorIcons[0], colorIcons[1])
                                entry.isAllColor && colorIcons.isNotEmpty() -> colorIcons[0]
                                else -> AllIcons.FileTypes.Css
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

                            val elt = LookupElementBuilder
                                .create(entry.rawName)
                                .withPresentableText(entry.display)
                                .withLookupString(entry.display)
                                .withIcon(icon)
                                .withTypeText(valueText, true)
                                .withTailText(if (short.isNotBlank()) " — $short" else "", true)
                                .withInsertHandler { ctx2, _ ->
                                    try {
                                        val doc = ctx2.document
                                        val start = ctx2.startOffset
                                        val tail = ctx2.tailOffset

                                        if (start >= 0 && tail <= doc.textLength && start <= tail) {
                                            doc.replaceString(start, tail, entry.rawName)
                                        }
                                    } catch (ex: Exception) {
                                        logger.debug("Safe insert handler caught exception", ex)
                                    }
                                }

                            result.addElement(elt)
                            entriesAdded++
                        }

                        logger.debug("Added $entriesAdded completion entries using strategy: ${varContext.strategy}")

                        // Handle IDE completion filtering
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

                        val endTime = System.currentTimeMillis()
                        logger.info("CSS Variable completion took ${endTime - startTime}ms for $entriesAdded entries")

                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("CSS var completion error", e)
                    }
                }
            }
        )
    }

    data class VarContext(
        val isInVar: Boolean,
        val strategy: String,
        val reason: String = ""
    )

    /**
     * ROBUST VAR() DETECTION: Multiple strategies with detailed logging
     */
    private fun detectVarContext(params: CompletionParameters): VarContext {
        val pos = params.position
        val offset = params.offset
        val document = params.editor?.document

        try {
            // Strategy 1: PSI tree detection (most reliable when PSI is complete)
            val fn = PsiTreeUtil.getParentOfType(pos, CssFunction::class.java)
            if (fn?.name == "var") {
                val l = fn.lParenthesis?.textOffset
                val r = fn.rParenthesis?.textOffset
                if (l != null && (r == null || (offset > l && offset <= r))) {
                    logger.debug("Strategy 1 SUCCESS: Found var() via PSI tree at offset $offset")
                    return VarContext(true, "PSI_TREE")
                }
            }

            // Strategy 2: Document text analysis (works when PSI is incomplete)
            if (document != null) {
                val text = document.text

                // Look backward from cursor for var( pattern
                val searchStart = maxOf(0, offset - 200)
                val beforeCursor = text.substring(searchStart, minOf(offset, text.length))

                // Pattern 1: Look for var( followed by optional whitespace and --
                val varPattern1 = Regex("""var\s*\(\s*--?[^)]*$""")
                if (varPattern1.find(beforeCursor) != null) {
                    logger.debug("Strategy 2a SUCCESS: Found var(-- pattern before cursor")
                    return VarContext(true, "TEXT_PATTERN_COMPLETE")
                }

                // Pattern 2: Look for incomplete var( pattern
                val varPattern2 = Regex("""var\s*\(\s*-?$""")
                if (varPattern2.find(beforeCursor) != null) {
                    logger.debug("Strategy 2b SUCCESS: Found incomplete var( pattern")
                    return VarContext(true, "TEXT_PATTERN_INCOMPLETE")
                }

                // Pattern 3: Look for var( anywhere with cursor inside parentheses
                val allVarMatches = Regex("""var\s*\(""").findAll(beforeCursor + text.substring(offset, minOf(offset + 50, text.length)))
                for (match in allVarMatches) {
                    val varStart = searchStart + match.range.last
                    val closingParen = text.indexOf(')', varStart)
                    if (closingParen == -1 || offset <= closingParen) {
                        logger.debug("Strategy 2c SUCCESS: Cursor inside var() parentheses")
                        return VarContext(true, "TEXT_PATTERN_INSIDE_PARENS")
                    }
                }
            }

            // Strategy 3: Element context detection (fallback)
            val elementText = pos.text
            val parentText = pos.parent?.text ?: ""
            val grandParentText = pos.parent?.parent?.text ?: ""

            if (elementText.contains("--") ||
                parentText.contains("var(") ||
                grandParentText.contains("var(")) {
                logger.debug("Strategy 3 SUCCESS: Found var context in element hierarchy")
                return VarContext(true, "ELEMENT_CONTEXT")
            }

            // Strategy 4: Character-based detection (most aggressive)
            if (document != null) {
                val text = document.text
                val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
                val lineText = text.substring(lineStart, minOf(document.getLineEndOffset(document.getLineNumber(offset)), text.length))

                // Check if line contains var( and we're after it
                val varIndex = lineText.indexOf("var(")
                if (varIndex != -1) {
                    val varAbsolutePos = lineStart + varIndex + 4 // position after "var("
                    if (offset >= varAbsolutePos) {
                        val closingParen = lineText.indexOf(')', varIndex)
                        if (closingParen == -1 || offset <= lineStart + closingParen) {
                            logger.debug("Strategy 4 SUCCESS: Line-based var() detection")
                            return VarContext(true, "LINE_BASED")
                        }
                    }
                }
            }

            logger.debug("All strategies FAILED - not in var() context")
            return VarContext(false, "NONE", "No var() context detected by any strategy")

        } catch (e: Exception) {
            logger.debug("Error in var context detection: ${e.message}")
            return VarContext(false, "ERROR", "Exception during detection: ${e.message}")
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