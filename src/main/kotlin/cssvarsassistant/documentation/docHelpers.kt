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
import cssvarsassistant.util.CssTextUtil
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

                // Issue #21: when the inner reference has multiple non-uniform
                // values across selectors and there's no `default` to anchor to
                // (e.g. Radix `--scaling` defined only under five themed
                // `[data-scaling='…']` selectors), substituting any one of them
                // would silently lie about the runtime value. Leave the
                // `var(--ref)` token intact so the popup shows the expression
                // verbatim (e.g. `calc(8px * var(--scaling))`).
                //
                // Single-pass note: if the same `raw` contains another
                // unambiguous `var(...)` later, we also stop here. That
                // partial-substitution case is rare in practice and worth
                // handling only if reported.
                val defaultEntry = entries.find { it.context == "default" }
                val distinctValues = entries.map { it.value }.distinct()
                val ambiguous = defaultEntry == null && distinctValues.size > 1
                if (ambiguous) return ResolutionInfo(raw, raw, steps)

                val defVal = defaultEntry?.value ?: entries.firstOrNull()?.value
                if (defVal != null) {
                    // Issue #21: substitute the matched `var(--ref)` token IN
                    // PLACE so the surrounding text (e.g. `calc(8px * …)`) is
                    // preserved. Previously we recursed with `defVal` as the
                    // new raw, which dropped the wrapper entirely and caused
                    // `calc(8px * var(--scaling))` to display as bare `0.9`.
                    val substituted = raw.replaceRange(m.range, defVal)
                    return resolveVarValue(project, substituted, visited + ref, depth + 1, newSteps)
                }
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
// Returns the last `--varName: value;` declaration in the file, IGNORING
// matches that appear inside /* ... */ or // ... comments. Issue #18 Bug A:
// prior versions scanned raw text and picked up commented-out example
// declarations as if they were real overrides, so the popup/hover showed
// the wrong value. Comments are replaced with whitespace first so we still
// respect token boundaries.
fun lastLocalValueInFile(fileText: String, varName: String): String? =
    Regex("""\Q$varName\E\s*:\s*([^;]+);""")
        .findAll(CssTextUtil.stripCssComments(fileText))
        .map { it.groupValues[1].trim() }
        .lastOrNull()
