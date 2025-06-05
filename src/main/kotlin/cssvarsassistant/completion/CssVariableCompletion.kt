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
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.completion.CssVarKeyCache
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ArithmeticEvaluator
import cssvarsassistant.util.safeIndexLookup
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon


class CssVariableCompletion : CompletionContributor() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)
    private val ENTRY_SEP = "|||"

    data class Entry(
        val rawName: String,
        val display: String,
        val mainValue: String,
        val allValues: List<Pair<String, String>>,
        val doc: String,
        val isAllColor: Boolean
    )

    companion object {
        private const val WIDTH_THRESHOLD = 50
        private const val PIXELS_PER_CHAR = 8
        private const val MIN_POPUP_WIDTH = 500
        private const val MAX_POPUP_WIDTH = 1100


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
            throw e // Always rethrow ProcessCanceledException
        } catch (e: Exception) {
            logger.warn("Failed to find preprocessor variable: $varName", e)
            null
        }
    }


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
                        val project = params.position.project
                        if (DumbService.isDumb(project)) return

                        // Check for cancellation at the start
                        ProgressManager.checkCanceled()

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
                        val settings = CssVarsAssistantSettings.getInstance()

                        // FIXED: Use CSS indexing scope for FileBasedIndex operations
                        val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)


                        val processedVariables = mutableSetOf<String>()

fun resolveVarValue(
    raw: String,
    visited: Set<String> = emptySet(),
    depth: Int = 0
): String {
    val resolveSettings = CssVarsAssistantSettings.getInstance()
    if (depth > resolveSettings.maxImportDepth) return raw

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

                val refEntries = safeIndexLookup(project) {
                    FileBasedIndex.getInstance()
                        .getValues(CSS_VARIABLE_INDEXER_NAME, ref, cssScope)
                        .toList()
                }.flatMap { it.split(ENTRY_SEP) }
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
                    val resolved = resolveVarValue(refDefault, visited + ref, depth + 1)
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
                val name = m.groupValues[1]
                if (name in visited) return@forEach

                val cached = CssVarCompletionCache.get(project, name)
                val resolved = cached ?: findPreprocessorVariableValue(project, name)
                if (resolved != null) {
                    CssVarCompletionCache.put(project, name, resolved)
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
        logger.warn("Failed to resolve variable value: $raw", e)
        return raw
    }
}

                        val entries = mutableListOf<Entry>()

                        // Check cancellation before expensive indexing operations
                        ProgressManager.checkCanceled()

                        val keyCache = CssVarKeyCache.get(project)
                        keyCache.getKeys()
                            .forEach { rawName ->
                                // Check cancellation periodically in loops
                                ProgressManager.checkCanceled()

                                val display = rawName.removePrefix("--")
                                if (!display.startsWith(simple, ignoreCase = true)) return@forEach

                                processedVariables.add(rawName)

                                val allVals = safeIndexLookup(project) {
                                    FileBasedIndex.getInstance()
                                        .getValues(CSS_VARIABLE_INDEXER_NAME, rawName, cssScope)
                                        .toList()
                                }.flatMap { it.split(ENTRY_SEP) }
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
                            // Check cancellation in completion generation loop
                            ProgressManager.checkCanceled()

                            val short = e.doc.takeIf { it.isNotBlank() }
                                ?.let { it.take(40) + if (it.length > 40) "…" else "" }
                                ?: ""

                            val colorIcons = e.allValues.mapNotNull { (_, v) ->
                                ColorParser.parseCssColor(v)?.let { ColorIcon(12, it, false) }
                            }.distinctBy { it.iconColor }

                            val icon: Icon = when {
                                e.isAllColor && colorIcons.size == 2 -> DoubleColorIcon(colorIcons[0], colorIcons[1])
                                e.isAllColor && colorIcons.isNotEmpty() -> colorIcons[0]
                                else -> AllIcons.FileTypes.Css
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
                                    try {
                                        val doc = ctx2.document
                                        val start = ctx2.startOffset
                                        val tail = ctx2.tailOffset

                                        // Validate range before replacement
                                        if (start >= 0 && tail <= doc.textLength && start <= tail) {
                                            doc.replaceString(start, tail, e.rawName)
                                        }
                                    } catch (ex: Exception) {
                                        // Log but don't crash completion
                                        logger.debug("Safe insert handler caught exception", ex)
                                    }
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
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        logger.error("CSS var completion error", e)
                    }
                }
            }
        )
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