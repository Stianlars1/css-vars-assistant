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
import cssvarsassistant.documentation.resolveVarValue
import cssvarsassistant.feedback.RatePromptService
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.CssVariableIndexValueCodec
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

private const val COMPLETION_LOOKUP_ELEMENT_PRIORITY_BASE = 10000

internal fun shouldStopAfterCssVarCompletion(
    entryCount: Int,
    allowIdeCompletions: Boolean
): Boolean = entryCount > 0 || !allowIdeCompletions

class CssVariableCompletion : CompletionContributor() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

    data class Entry(
        val rawName: String,
        val display: String,
        val mainValue: String,
        val allValues: List<Pair<String, String>>,
        val doc: String,
        val isAllColor: Boolean,
        val derived: Boolean,
        val matchPriority: Int = 0,
        val matchedQuery: String = ""

    ) {
        constructor(
            rawName: String,
            display: String,
            mainValue: String,
            allValues: List<Pair<String, String>>,
            doc: String,
            isAllColor: Boolean,
            derived: Boolean
        ) : this(rawName, display, mainValue, allValues, doc, isAllColor, derived, 0, "")
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
                    val startTime = System.currentTimeMillis()
                    try {
                        val project = params.position.project
                        if (DumbService.isDumb(project)) return
                        ProgressManager.checkCanceled()

                        val insideVarFunction = isInsideVarFunction(params)
                        if (!insideVarFunction) return

                        val caretOffset = params.editor.caretModel.offset
                        val query = CssVarQueryMatcher.withFallback(
                            primary = CssVarQueryMatcher.extractQuery(params.originalFile.text, caretOffset)
                                ?: CssVarQueryMatcher.extractQuery(params.editor.document.text, caretOffset)
                                ?: CssVarQueryMatcher.extractQuery(params.editor.document.text, params.offset),
                            prefixMatcherPrefix = result.prefixMatcher.prefix
                        )
                        val activeQuery = query ?: CssVarQueryMatcher.Query("", "")

                        // Permissive prefix matcher that keeps the correct prefix LENGTH
                        // (so IntelliJ replaces the typed `--foo` when inserting the lookup
                        // string) but accepts every element we add — our own
                        // CssVarQueryMatcher already decides what to surface. This also
                        // works for `var()` nested inside other CSS functions
                        // (hsl/rgb/calc/color-mix/etc.) because `activeQuery.rawPrefix`
                        // is always the text typed inside the innermost `var(`.
                        val completionResult = result.withPrefixMatcher(
                            object : PrefixMatcher(activeQuery.rawPrefix) {
                                override fun prefixMatches(name: String): Boolean = true
                                override fun cloneWithPrefix(prefix: String): PrefixMatcher = this
                            }
                        )

                        val settings = CssVarsAssistantSettings.getInstance()
                        val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)
                        val keyCache = CssVarKeyCache.get(project)

                        val entries = mutableListOf<Entry>()

                        /* ---------------------------------------------------- */
                        /*  for hver variabel-key                               */
                        /* ---------------------------------------------------- */
                        val activeFileText = params.originalFile.text

                        keyCache.keys(cssScope).forEach { rawName ->
                            ProgressManager.checkCanceled()

                            val display = rawName.removePrefix("--")
                            val match = CssVarQueryMatcher.bestMatch(display, activeQuery) ?: return@forEach

                            /* ---- hent alle values -------------------------- */
                            val allVals = CssVariableIndexValueCodec.decode(
                                FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, rawName, cssScope)
                            ).distinct()

                            if (allVals.isEmpty()) return@forEach

                            /* ---- map til (context, resolved) --------------- */
                            var didResolve = false

                            val valuePairs = allVals.mapNotNull {
                                val resolved = resolveVarValue(project, it.value).resolved
                                if (resolved != it.value) didResolve = true
                                it.context to resolved
                            }


                            val uniquePairs = valuePairs
                                .asReversed()
                                .distinctBy { it.first to it.second }     // beholder én rad per (context,value)
                                .asReversed()

                            /* --- finn cascade-vinner --- */
                            val mainValue = CssVarCascadeUtil.selectMainValue(rawName, activeFileText, uniquePairs)


                            val docEntry = allVals.firstOrNull { it.comment.isNotBlank() } ?: allVals.first()
                            val commentTxt = docEntry.comment
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
                                didResolve,
                                match.kind.priority,
                                match.matchedPrefix
                            )
                        }

                        val strongestEntries = CssVarQueryMatcher
                            .keepStrongestMatches(entries, activeQuery) { it.display }
                            .toMutableList()

                        /* ------------- sortering -------------------------- */
                        strongestEntries.sortWith(
                            createSmartComparator(settings.sortingOrder, activeQuery.normalizedPrefix)
                        )

                        /* ------------- bygg Lookup-elementer -------------- */
                        val descMaxLen =
                            if (settings.showCompletionDescription) settings.completionDescriptionMaxLength
                            else 0
                        strongestEntries.forEachIndexed { idx, e ->
                            ProgressManager.checkCanceled()
                            val priority = (COMPLETION_LOOKUP_ELEMENT_PRIORITY_BASE - idx).toDouble()

                            val shortDoc = if (descMaxLen <= 0) "" else e.doc
                                .takeIf { it.isNotBlank() }
                                ?.let {
                                    val trimmed = it.lineSequence()
                                        .map(String::trim)
                                        .filter { line -> line.isNotBlank() }
                                        .joinToString(" ")
                                    if (trimmed.length > descMaxLen) trimmed.take(descMaxLen) + "…"
                                    else trimmed
                                } ?: ""

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

                            completionResult.addElement(
                                PrioritizedLookupElement
                                    .withPriority(element, priority)
                            )
                        }

                        if (shouldStopAfterCssVarCompletion(strongestEntries.size, settings.allowIdeCompletions)) {
                            result.stopHere()
                        }

                        if (strongestEntries.isNotEmpty()) {
                            RatePromptService.getInstance().recordUsage()
                        }

                        logger.info(
                            "CSS var completion: ${System.currentTimeMillis() - startTime}ms, " +
                                    "${strongestEntries.size} entries."
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


    private fun createSmartComparator(
        order: CssVarsAssistantSettings.SortingOrder,
        query: String
    ): Comparator<Entry> {
        val baseComparator = Comparator<Entry> { a, b ->
            if (a.matchPriority != b.matchPriority) {
                return@Comparator a.matchPriority - b.matchPriority
            }

            compareMatchedNumericFamily(a, b, query)?.let { matchedFamilyOrder ->
                if (matchedFamilyOrder != 0) {
                    return@Comparator matchedFamilyOrder
                }
            }

            compareQuerySpecificity(a, b)?.let { specificityOrder ->
                if (specificityOrder != 0) {
                    return@Comparator specificityOrder
                }
            }

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

    private fun compareMatchedNumericFamily(a: Entry, b: Entry, query: String): Int? {
        if (query.isBlank()) return null

        val aNumericSuffix = numericSuffixForQuery(a.display, query)
        val bNumericSuffix = numericSuffixForQuery(b.display, query)

        return when {
            aNumericSuffix != null && bNumericSuffix != null -> aNumericSuffix.compareTo(bNumericSuffix)
            aNumericSuffix != null -> -1
            bNumericSuffix != null -> 1
            else -> null
        }
    }

    private fun compareQuerySpecificity(a: Entry, b: Entry): Int? {
        if (a.matchedQuery.isBlank() || b.matchedQuery.isBlank()) {
            return null
        }

        val aExact = a.display.equals(a.matchedQuery, ignoreCase = true)
        val bExact = b.display.equals(b.matchedQuery, ignoreCase = true)
        if (aExact != bExact) {
            return if (aExact) -1 else 1
        }

        val aType = ValueUtil.getValueType(a.mainValue)
        val bType = ValueUtil.getValueType(b.mainValue)
        if (aType in setOf(ValueUtil.ValueType.SIZE, ValueUtil.ValueType.NUMBER) &&
            bType in setOf(ValueUtil.ValueType.SIZE, ValueUtil.ValueType.NUMBER)
        ) {
            return null
        }

        val aStartsWithMatch = a.display.startsWith(a.matchedQuery, ignoreCase = true)
        val bStartsWithMatch = b.display.startsWith(b.matchedQuery, ignoreCase = true)
        if (aStartsWithMatch && bStartsWithMatch) {
            val lengthOrder = a.display.length.compareTo(b.display.length)
            if (lengthOrder != 0) {
                return lengthOrder
            }
        }

        return null
    }

    private fun numericSuffixForQuery(displayName: String, query: String): Int? {
        val normalizedPrefix = "${query.lowercase()}-"
        val normalizedName = displayName.lowercase()
        if (!normalizedName.startsWith(normalizedPrefix)) return null

        return normalizedName
            .removePrefix(normalizedPrefix)
            .toIntOrNull()
    }

    /**
     * Returns true if the caret sits strictly inside an unclosed `var(...)`
     * expression — i.e. between its opening `(` and the matching `)`.
     *
     * Issue #18 Bug B: the old line-based check returned true whenever the
     * caret was after *any* `var(` on the line, even when the matching `)`
     * had already been passed. That caused the plugin to offer every indexed
     * CSS variable on lines that happened to contain a `var()` call
     * somewhere, flooding completion with irrelevant suggestions.
     *
     * Correctness rules enforced here:
     *   1. PSI check first (authoritative when available).
     *   2. Text fallback tracks paren depth from each `var(` until the caret
     *      or the matching `)`. Depth must stay ≥ 1 at the caret.
     *   3. `(?<![\w-])var\s*\(` stops identifiers like `myvar(` from counting.
     */
    private fun isInsideVarFunction(params: CompletionParameters): Boolean {
        val offset = params.offset

        // 1. PSI-first, the authoritative answer when available.
        try {
            val fn = PsiTreeUtil.getParentOfType(params.position, CssFunction::class.java)
            if (fn != null && fn.name.equals("var", ignoreCase = true)) {
                val l = fn.lParenthesis?.textOffset
                val r = fn.rParenthesis?.textOffset
                if (l != null && offset > l && (r == null || offset <= r)) {
                    logger.debug("✅ PSI detected var() context")
                    return true
                }
            }
        } catch (e: Exception) {
            logger.debug("PSI var() probe failed: ${e.message}")
        }

        // 2. Textual fallback — used in dumb mode, non-indexed files, and the
        //    SCSS/LESS contexts where the CSS PSI sometimes does not see var.
        return try {
            val text = params.editor.document.text
            val searchStart = maxOf(0, offset - 200)
            val searchText = text.substring(searchStart, offset)
            val cursorInSearch = offset - searchStart

            val varMatches = VAR_OPEN_REGEX.findAll(searchText).toList()
            if (varMatches.isEmpty()) {
                logger.debug("❌ No var( before caret")
                return false
            }

            // Prefer the innermost `var(` that still contains the caret: walk
            // the matches in reverse. For each candidate, simulate paren depth
            // from its `(` up to the caret; if depth stays ≥ 1 the caret is
            // inside that var(...) call.
            for (match in varMatches.asReversed()) {
                val openParenIndex = match.range.last
                if (cursorInSearch <= openParenIndex) continue

                var depth = 1
                var i = openParenIndex + 1
                while (i < cursorInSearch) {
                    when (searchText[i]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    i++
                }
                if (depth >= 1) {
                    logger.debug("✅ Text scan confirms inside var(), depth=$depth")
                    return true
                }
            }

            logger.debug("❌ Caret is outside every var() before it")
            false
        } catch (e: Exception) {
            logger.debug("Text var() probe failed: ${e.message}")
            false
        }
    }

    private companion object {
        // Guarded with a non-word look-behind so identifiers that merely end
        // in "var" (e.g. `myvar(`, `ivar(`) don't count as a var() call.
        val VAR_OPEN_REGEX = Regex("""(?<![A-Za-z0-9_-])var\s*\(""", RegexOption.IGNORE_CASE)
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
