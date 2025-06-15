package cssvarsassistant.documentation.v1

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVariableCompletion
import cssvarsassistant.documentation.buildHtmlDocument
import cssvarsassistant.documentation.extractVariableName
import cssvarsassistant.documentation.resolveVarValue
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.model.DocParser
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.RankUtil.rank
import cssvarsassistant.util.ScopeUtil
import cssvarsassistant.util.ValueUtil
import kotlin.math.roundToInt


val ENTRY_SEP = "|||"

class CssVariableDocumentation : AbstractDocumentationProvider() {
    private val logger = Logger.getInstance(CssVariableCompletion::class.java)


    /* ---------------------------------------------------------------------- */
    /*  generateDoc()                                                         */
    /* ---------------------------------------------------------------------- */
    override fun generateDoc(element: PsiElement, original: PsiElement?): String? {
        try {
            val project = element.project
            if (DumbService.isDumb(project)) return null
            ProgressManager.checkCanceled()

            val settings = CssVarsAssistantSettings.getInstance()
            val varName = extractVariableName(element) ?: return null
            val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)

            /* ------------------- gather all values for this variable -------- */
            val rawEntries = FileBasedIndex.getInstance()
                .getValues(CSS_VARIABLE_INDEXER_NAME, varName, cssScope)
                .flatMap { it.split(ENTRY_SEP) }
                .filter { it.isNotBlank() }

            if (rawEntries.isEmpty()) return null

            // parsed = Triple(context, ResolveInfo, comment)
            val parsed = rawEntries.mapNotNull {
                val parts = it.split(DELIMITER, limit = 3)
                if (parts.size >= 2) {
                    val ctx = parts[0]
                    val resInfo = resolveVarValue(project, parts[1])
                    val comment = parts.getOrElse(2) { "" }
                    Triple(ctx, resInfo, comment)
                } else null
            }

            // collapse «last wins» per context
            val collapsed = parsed
                .groupBy { it.first }
                .mapValues { (_, list) -> list.last() }
                .values.toList()

            val sorted = collapsed.sortedWith(
                compareBy(
                    { rank(it.first).first },
                    { rank(it.first).second ?: Int.MAX_VALUE },
                    { rank(it.first).third }
                )
            )

            /* ---------- Doc-parser for heading/description ------------------ */
            val docEntry = collapsed.firstOrNull { it.third.isNotBlank() }
                ?: collapsed.find { it.first == "default" }
                ?: collapsed.first()
            val doc = DocParser.parse(docEntry.third, docEntry.second.resolved)

            /* -------------- do we need extra columns? ------------------------- */
            val showPixelCol = sorted.any { (_, res, _) ->
                if (!ValueUtil.isSizeValue(res.resolved)) return@any false
                val unit = res.resolved.replace(Regex("[0-9.+\\-]"), "").trim().lowercase()
                val pxVal = ValueUtil.convertToPixels(res.resolved)
                val numericRaw = res.resolved.replace(Regex("[^0-9.+\\-]"), "")
                    .toDoubleOrNull() ?: pxVal
                unit != "px" || pxVal.roundToInt() != numericRaw.roundToInt()
            }

            return buildHtmlDocument(
                varName,
                doc,
                sorted,
                showPixelCol,
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error generating documentation", e)
            return null
        }
    }
}