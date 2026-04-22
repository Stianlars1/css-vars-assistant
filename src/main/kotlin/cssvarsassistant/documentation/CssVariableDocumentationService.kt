// CssVariableDocumentationService.kt
package cssvarsassistant.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.CssVariableIndexValueCodec
import cssvarsassistant.index.IndexedCssVariableValue
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.CssTextUtil
import cssvarsassistant.util.RankUtil.rank
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

            val settings = CssVarsAssistantSettings.getInstance()
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            // Phase 8b / issue #19: `processValues` yields `(VirtualFile, packed)`
            // per indexed file, so we can attribute each decoded entry back to
            // the file it came from. `getValues` would have dropped that
            // mapping. The plan explicitly rejects `getContainingFiles` + a
            // follow-up re-query; that's O(files × keys) and duplicates work
            // the index has already done.
            val rawEntries = mutableListOf<RawEntryWithFile>()
            FileBasedIndex.getInstance().processValues(
                CSS_VARIABLE_INDEXER_NAME,
                varName,
                null,
                { file, packed ->
                    CssVariableIndexValueCodec.decodePacked(packed).forEach { decoded ->
                        rawEntries += RawEntryWithFile(decoded, file.name)
                    }
                    true
                },
                cssScope
            )

            if (rawEntries.isEmpty()) return null

            val parsed = rawEntries.map { withFile ->
                val entry = withFile.value
                val resInfo = resolveVarValue(project, entry.value)
                ParsedEntry(
                    context = entry.context,
                    rawValue = entry.value,
                    resInfo = resInfo,
                    comment = entry.comment,
                    sourceFile = withFile.sourceFile,
                    sourceLine = entry.line
                )
            }

            // Get local file text and find local values
            val activeText = element.containingFile.text
            val localValues = extractLocalValues(activeText, varName)

            // Mark entries as local or imported
            val enrichedEntries = parsed.map { entry ->
                EntryWithSource(
                    context = entry.context,
                    rawValue = entry.rawValue,
                    resInfo = entry.resInfo,
                    comment = entry.comment,
                    sourceFile = entry.sourceFile,
                    sourceLine = entry.sourceLine,
                    isLocal = isLocalDeclaration(entry.rawValue, localValues)
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

            // Find cascade winner using CSS rules
            val winnerIndex = findCascadeWinner(sorted)

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

            val hoverRows = sorted.map {
                HoverRow(
                    context = it.context,
                    resInfo = it.resInfo,
                    comment = it.comment,
                    sourceFile = it.sourceFile,
                    sourceLine = it.sourceLine
                )
            }

            return buildHtmlDocument(varName, doc, hoverRows, showPixelCol, winnerIndex)


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
        val sourceLine: Int?
    )

    private data class RawEntryWithFile(
        val value: IndexedCssVariableValue,
        val sourceFile: String
    )

    private fun extractLocalValues(fileText: String, varName: String): Set<String> {
        // Issue #18 Bug A mirror: the same comment-leak that affected completion
        // also affected the "is this a local declaration?" check here, causing
        // commented-out example declarations to be counted as local.
        return Regex("""\Q$varName\E\s*:\s*([^;]+);""")
            .findAll(CssTextUtil.stripCssComments(fileText))
            .map { it.groupValues[1].trim() }
            .toSet()
    }

    private fun isLocalDeclaration(rawValue: String, localValues: Set<String>): Boolean {
        return localValues.contains(rawValue)
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
        val settings = CssVarsAssistantSettings.getInstance()
        val scope = ScopeUtil.effectiveCssIndexingScope(project, settings)

        return try {
            val rawEntries = CssVariableIndexValueCodec.decode(
                FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, scope)
            )

            // Use same cascade logic for hint generation
            val activeText = element.containingFile.text
            val localWinner = lastLocalValueInFile(activeText, varName)

            val resolutionInfo = if (localWinner != null) {
                // Local declaration - resolve it to get steps
                resolveVarValue(project, localWinner)
            } else {
                // Fallback to indexed values
                rawEntries.let { list ->
                    val rawValue = list.find { it.context == "default" }?.value ?: list.firstOrNull()?.value
                    rawValue?.let { resolveVarValue(project, it) }
                }
            }

            // Return resolution steps if they exist, otherwise just the final value
            resolutionInfo?.let { info ->
                val result = if (info.steps.isNotEmpty() && info.original != info.resolved) {
                    "Resolution: ${info.steps.joinToString(" → ")} → ${info.resolved}"
                } else {
                    "$varName → ${info.resolved}"
                }
                result
            }
        } catch (e: Exception) {
            logger.warn("Failed to generate hint for $varName", e)
            null
        }
    }
}
