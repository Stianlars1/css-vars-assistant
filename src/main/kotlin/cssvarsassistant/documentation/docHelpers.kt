package cssvarsassistant.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.CssVariableIndexValueCodec
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

/* ───────────────────────── Dochelper ─────────────────────────────────────────────── */

data class ResolutionInfo(val original: String, val resolved: String, val steps: List<String> = emptyList())

private val LOG = Logger.getInstance("cssvarsassistant.docHelpers")

/* ────────────────────── resolver ─────────────────────────────── */
fun resolveVarValue(
    project: Project,
    raw: String,
    visited: Set<String> = emptySet(),
    depth: Int = 0,
    steps: List<String> = emptyList()
): ResolutionInfo {
    val settings = CssVarsAssistantSettings.getInstance()
    if (depth > settings.maxImportDepth) return ResolutionInfo(raw, raw, steps)

    try {
        ProgressManager.checkCanceled()

        Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)?.let { m ->
            val ref = m.groupValues[1]
            if (ref !in visited) {
                val newSteps = steps + "var($ref)"
                val cssScope = ScopeUtil.effectiveCssIndexingScope(project, settings)
                val entries = CssVariableIndexValueCodec.decode(
                    FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, ref, cssScope)
                )

                val defVal = entries
                    .let { list ->
                        list.find { it.context == "default" }?.value ?: list.firstOrNull()?.value
                    }
                if (defVal != null)
                    return resolveVarValue(project, defVal, visited + ref, depth + 1, newSteps)
            }
            return ResolutionInfo(raw, raw, steps)
        }

        val preprocessorMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())
        if (preprocessorMatch != null) {
            val varName = preprocessorMatch.groupValues[1]
            val resolution = findPreprocessorVariableValue(project, varName, steps)
            if (resolution != null && resolution.resolved != raw) {
                return ResolutionInfo(
                    original = raw,
                    resolved = resolution.resolved,
                    steps = resolution.steps
                )
            }
        }

        return ResolutionInfo(raw, raw, steps)
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Error resolving variable value", e)
        return ResolutionInfo(raw, raw, steps)
    }
}


/* ────────────────────── Recursive Preprocessor finder ──────────────────────── */
fun findPreprocessorVariableValue(
    project: Project,
    varName: String,
    currentSteps: List<String> = emptyList()
): ResolutionInfo? {
    return try {
        val freshScope = ScopeUtil.currentPreprocessorScope(project)
        val resolution = PreprocessorUtil.resolveVariableWithSteps(
            project,
            varName,
            freshScope,
            emptySet(),
            currentSteps
        )
        return resolution
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Failed to resolve @$varName", e)
        null
    }
}

/* ────────────────────── ** extractor ──────────────────────── */

fun extractCssVariableName(element: PsiElement): String? {
    // Always check element validity first
    if (!element.isValid) return null

    // Safe handling of potentially null text
    val elementText = element.text
    if (elementText?.trim()?.startsWith("--") == true) {
        return elementText.trim()
    }

    // Check parent element safely
    val parent = element.parent
    if (parent?.isValid == true) {
        val parentText = parent.text
        if (parentText != null) {
            return Regex("""var\(\s*(--[\w-]+)\s*\)""").find(parentText)?.groupValues?.get(1)
        }
    }

    return null
}

/* ─────────────────────── other helpers ───────────────────────── */
fun lastLocalValueInFile(fileText: String, varName: String): String? =
    Regex("""\Q$varName\E\s*:\s*([^;]+);""")
        .findAll(fileText)
        .map { it.groupValues[1].trim() }
        .lastOrNull()
