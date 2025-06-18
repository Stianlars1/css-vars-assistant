// CssVariableDocumentationService.kt
package cssvarsassistant.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.documentation.tooltip.CssVariableDocumentationHyperlinkListener
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.RankUtil.rank
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil
import javax.swing.event.HyperlinkListener
import kotlin.math.roundToInt

val ENTRY_SEP = "|||"

object CssVariableDocumentationService {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)

    fun generateDocumentation(element: PsiElement, varName: String): String? {
        try {
            val project = element.project
            if (DumbService.isDumb(project)) return null
            ProgressManager.checkCanceled()

            val settings = CssVarsAssistantSettings.getInstance()
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            val rawEntries = FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
                .flatMap { it.split(ENTRY_SEP) }.filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            val parsed = rawEntries.mapNotNull {
                val parts = it.split(DELIMITER, limit = 3)
                if (parts.size >= 2) {
                    val ctx = parts[0]
                    val resInfo = resolveVarValue(project, parts[1])
                    val comment = parts.getOrElse(2) { "" }
                    Triple(ctx, resInfo, comment)
                } else null
            }

            // Get local file text and find local values
            val activeText = element.containingFile.text
            val localValues = extractLocalValues(activeText, varName)

            // Mark entries as local or imported
            val enrichedEntries = parsed.map { (ctx, resInfo, comment) ->
                val isLocal = isLocalDeclaration(resInfo.resolved, localValues)
                EntryWithSource(ctx, resInfo, comment, isLocal)
            }

            val collapsed = enrichedEntries
                .asReversed()
                .distinctBy { it.context to it.resInfo.resolved }
                .asReversed()

            val sorted = collapsed.sortedWith(
                compareBy(
                    { rank(it.context).first },
                    { rank(it.context).second ?: Int.MAX_VALUE },
                    { rank(it.context).third }
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

            // Convert back to original format
            val sortedTriples = sorted.map { Triple(it.context, it.resInfo, it.comment) }

            return buildHtmlDocumentWithHyperlinks(varName, doc, sortedTriples, showPixelCol, winnerIndex, element)


        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }

    // Add method to get hyperlink listener for the documentation
    fun createHyperlinkListener(element: PsiElement, varName: String): HyperlinkListener {
        return CssVariableDocumentationHyperlinkListener(element.project, varName)
    }

    private data class EntryWithSource(
        val context: String,
        val resInfo: ResolutionInfo,
        val comment: String,
        val isLocal: Boolean
    )

    private fun extractLocalValues(fileText: String, varName: String): Set<String> {
        return Regex("""\Q$varName\E\s*:\s*([^;]+);""")
            .findAll(fileText)
            .map { it.groupValues[1].trim() }
            .toSet()
    }

    private fun isLocalDeclaration(resolvedValue: String, localValues: Set<String>): Boolean {
        return localValues.contains(resolvedValue)
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
            val rawEntries = FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, scope)
                .flatMap { it.split(ENTRY_SEP) }.filter { it.isNotBlank() }

            // Use same cascade logic for hint generation
            val activeText = element.containingFile.text
            val localWinner = lastLocalValueInFile(activeText, varName)

            val resolutionInfo = if (localWinner != null) {
                // Local declaration - resolve it to get steps
                resolveVarValue(project, localWinner)
            } else {
                // Fallback to indexed values
                rawEntries.mapNotNull {
                    val parts = it.split(DELIMITER, limit = 3)
                    if (parts.size >= 2) parts[0] to parts[1] else null
                }.let { list ->
                    val rawValue = list.find { it.first == "default" }?.second ?: list.firstOrNull()?.second
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
                logger.info("generateHint returning: $result") // Add this line
                result
            }
        } catch (e: Exception) {
            logger.warn("Failed to generate hint for $varName", e)
            null
        }
    }
}