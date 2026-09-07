// CssVariableDocumentationService.kt
package cssvarsassistant.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.index.IndexedCssVariableValue
import cssvarsassistant.index.VariableLookup
import cssvarsassistant.index.VariableLocation
import cssvarsassistant.index.PreprocessorLookup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil
import kotlin.math.roundToInt

object CssVariableDocumentationService {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

    fun generateDocumentation(element: PsiElement, varName: String): String? {
        try {
            val project = element.project
            if (DumbService.isDumb(project)) return null
            ProgressManager.checkCanceled()

            if (isPreprocessorVariable(varName)) {
                return generatePreprocessorDocumentation(element, varName)
            }

            return generateCssVarDocumentation(element, displayName = varName, lookupName = varName)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }

    private fun generateCssVarDocumentation(
        element: PsiElement,
        displayName: String,
        lookupName: String
    ): String? {
        try {
            val project = element.project
            if (DumbService.isDumb(project)) return null
            ProgressManager.checkCanceled()

            val settings = CssVarsAssistantSettings.getInstance()
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            // Phase 8b / issue #19: `processValues` yields `(VirtualFile, packed)`
            // per indexed file, so we can attribute each decoded entry back to
            // the file it came from. `getValues` would have dropped that
            // mapping. The plan explicitly rejects `getContainingFiles` + a
            // follow-up re-query; that's O(files × keys) and duplicates work
            // the index has already done.
            val rawEntries = VariableLookup.cssValues(project, lookupName, cssScope)
                .map { RawEntryWithFile(it.value, it.file.name, it.file) }
            val resolver = VariableResolver(project)

            if (rawEntries.isEmpty()) return null

            val parsed = rawEntries.map { withFile ->
                val entry = withFile.value
                val resInfo = resolver.resolve(entry.value, VariableLocation(withFile.file, entry.offset.takeIf { it >= 0 } ?: Int.MAX_VALUE, cssContext = entry.context))
                ParsedEntry(
                    context = entry.context,
                    rawValue = entry.value,
                    resInfo = resInfo,
                    comment = entry.comment,
                    sourceFile = withFile.sourceFile,
                    sourceLine = entry.line,
                    file = withFile.file
                )
            }

            // Mark entries as local or imported
            val enrichedEntries = parsed.map { entry ->
                EntryWithSource(
                    context = entry.context,
                    rawValue = entry.rawValue,
                    resInfo = entry.resInfo,
                    comment = entry.comment,
                    sourceFile = entry.sourceFile,
                    sourceLine = entry.sourceLine,
                    isLocal = entry.file == element.containingFile.virtualFile
                )
            }


            val collapsed = enrichedEntries
                .asReversed()
                .distinctBy { it.context to it.resInfo.resolved }
                .asReversed()

            val sorted = collapsed.sortedWith(
                hoverRowComparator(
                    context = { it.context },
                    sourceFile = { it.sourceFile },
                    sourceLine = { it.sourceLine }
                )
            )

            // 1.8.3 — winner is found BEFORE collapsing so we can later
            // identify which merged row corresponds to the runtime winner
            // and still bold/promote it. Once rows are merged their
            // `context` string is a comma-joined prettified label, not
            // `default`, so findCascadeWinner won't recognise them.
            val preCollapseWinnerIndex = findCascadeWinner(sorted)
            val preCollapseWinner = sorted.getOrNull(preCollapseWinnerIndex)

            val docEntry = collapsed.firstOrNull { it.comment.isNotBlank() }
                ?: collapsed.find { it.context == "default" }
                ?: collapsed.first()
            val doc = DocParser.parse(docEntry.comment, docEntry.resInfo.resolved)

            val showPixelCol = sorted.any { entry ->
                if (!ValueUtil.isSizeValue(entry.resInfo.resolved)) return@any false
                val unit = entry.resInfo.resolved.replace(Regex("[0-9.+\\-]"), "").trim().lowercase()
                val pxVal = ValueUtil.convertToPixels(entry.resInfo.resolved)
                val numericRaw = entry.resInfo.resolved.replace(Regex("[^0-9.+\\-]"), "").toDoubleOrNull() ?: pxVal
                unit != "px" || pxVal.roundToInt() != numericRaw.roundToInt()
            }

            // 1.8.3 — collapse rows with identical resolved values so large
            // theme systems don't render N near-duplicate rows. Each merged
            // row's Context column lists every contributing theme, prettified
            // ("Light mode, Catppuccin, Sepia"). Disabled keeps the 1.8.2
            // one-row-per-selector behaviour for users auditing each
            // declaration individually.
            val finalRows = if (settings.collapseIdenticalValues) {
                collapseRowsByValue(
                    rows = sorted,
                    value = { it.resInfo.resolved },
                    label = {
                        val isColor = ColorParser.parseCssColor(it.resInfo.resolved) != null
                        contextLabel(it.context, isColor, settings.prettifyThemeLabels)
                    },
                    merge = { first, mergedLabel -> first.copy(context = mergedLabel) },
                    combineWithDefault = { prettifySelector(it.context) != null }
                )
            } else {
                sorted
            }

            val winnerIndex = if (preCollapseWinner != null) {
                finalRows.indexOfFirst { it.resInfo.resolved == preCollapseWinner.resInfo.resolved }
            } else {
                -1
            }

            val hoverRows = finalRows.map {
                HoverRow(
                    context = it.context,
                    resInfo = it.resInfo,
                    comment = it.comment,
                    sourceFile = it.sourceFile,
                    sourceLine = it.sourceLine
                )
            }

            return buildHtmlDocument(displayName, doc, hoverRows, showPixelCol, winnerIndex)


        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }


    private data class EntryWithSource(
        val context: String,
        val rawValue: String,
        val resInfo: ResolutionInfo,
        val comment: String,
        val sourceFile: String?,
        val sourceLine: Int?,
        val isLocal: Boolean
    )

    private data class ParsedEntry(
        val context: String,
        val rawValue: String,
        val resInfo: ResolutionInfo,
        val comment: String,
        val sourceFile: String?,
        val sourceLine: Int?,
        val file: VirtualFile
    )

    private data class RawEntryWithFile(
        val value: IndexedCssVariableValue,
        val sourceFile: String,
        val file: VirtualFile
    )

    private fun location(element: PsiElement): VariableLocation = VariableLocation(
        element.containingFile.virtualFile,
        if (element is PsiFile) Int.MAX_VALUE else element.textRange.endOffset
    )

    private fun generatePreprocessorDocumentation(element: PsiElement, varName: String): String? {
        val project = element.project
        val scope = ScopeUtil.currentPreprocessorScope(project)
        val location = location(element)
        val source = PreprocessorLookup(project, scope).find(varName, location) ?: return null
        val resolver = VariableResolver(project, scope)
        val preprocessorOnly = resolver.resolve(varName, location, expandCss = false)
        extractCssVarAlias(preprocessorOnly.resolved)?.let { alias ->
            generateCssVarDocumentation(element, "$varName → var($alias)", alias)?.let { return it }
        }
        val resolution = resolver.resolve(varName, location)
        val rows = listOf(HoverRow("default", resolution, "", source.file.name, source.declaration.line))
        return buildHtmlDocument(varName, DocParser.parse("", resolution.resolved), rows, shouldShowPixelColumn(rows), 0)
    }

    private fun findCascadeWinner(sorted: List<EntryWithSource>): Int {
        val defaultEntries = sorted.withIndex().filter { it.value.context == "default" }

        if (defaultEntries.isEmpty()) {
            return sorted.indexOfLast { it.context == "default" }
        }

        // Prefer local declarations over imports
        val localDefaults = defaultEntries.filter { it.value.isLocal }
        if (localDefaults.isNotEmpty()) {
            return localDefaults.last().index
        }

        // If no local defaults, use imported defaults
        val importedDefaults = defaultEntries.filter { !it.value.isLocal }
        if (importedDefaults.isNotEmpty()) {
            return importedDefaults.last().index
        }

        return sorted.indexOfLast { it.context == "default" }
    }

    fun generateHint(element: PsiElement, varName: String): String? {
        val project = element.project
        if (DumbService.isDumb(project)) return null
        ProgressManager.checkCanceled()
        val resolver = VariableResolver(project)
        val location = location(element)
        val resolution = if (isPreprocessorVariable(varName)) {
            val scope = ScopeUtil.currentPreprocessorScope(project)
            if (PreprocessorLookup(project, scope).find(varName, location) == null) return null
            resolver.resolve(varName, location)
        } else {
            val scope = ScopeUtil.effectiveCssIndexingScope(project, CssVarsAssistantSettings.getInstance())
            val entries = VariableLookup.cssValues(project, varName, scope)
            val local = lastLocalValueInFile(element.containingFile.text, varName)
            val value = local ?: entries.lastOrNull { it.value.context == "default" }?.value?.value
                ?: entries.firstOrNull()?.value?.value ?: return null
            resolver.resolve(value, location)
        }
        return if (resolution.steps.isNotEmpty() && resolution.original != resolution.resolved) {
            "Resolution: ${resolution.steps.joinToString(" → ")} → ${resolution.resolved}"
        } else "$varName → ${resolution.resolved}"
    }

    private fun isPreprocessorVariable(varName: String): Boolean =
        varName.startsWith("$") || varName.startsWith("@") || varName.contains(".$")

    private fun shouldShowPixelColumn(rows: List<HoverRow>): Boolean =
        rows.any { entry ->
            if (!ValueUtil.isSizeValue(entry.resInfo.resolved)) return@any false
            val unit = entry.resInfo.resolved.replace(Regex("[0-9.+\\-]"), "").trim().lowercase()
            val pxVal = ValueUtil.convertToPixels(entry.resInfo.resolved)
            val numericRaw = entry.resInfo.resolved.replace(Regex("[^0-9.+\\-]"), "").toDoubleOrNull() ?: pxVal
            unit != "px" || pxVal.roundToInt() != numericRaw.roundToInt()
        }

}
