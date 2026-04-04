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
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
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

            val rawEntries = CssVariableIndexValueCodec.decode(
                FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
            )

            if (rawEntries.isEmpty()) return null

            val parsed = rawEntries.map { entry ->
                val resInfo = resolveVarValue(project, entry.value)
                ParsedEntry(entry.context, entry.value, resInfo, entry.comment)
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
                    isLocal = isLocalDeclaration(entry.rawValue, localValues)
                )
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

            return buildHtmlDocument(varName, doc, sortedTriples, showPixelCol, winnerIndex)


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
        val isLocal: Boolean
    )

    private data class ParsedEntry(
        val context: String,
        val rawValue: String,
        val resInfo: ResolutionInfo,
        val comment: String
    )

    private fun extractLocalValues(fileText: String, varName: String): Set<String> {
        return Regex("""\Q$varName\E\s*:\s*([^;]+);""")
            .findAll(fileText)
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
