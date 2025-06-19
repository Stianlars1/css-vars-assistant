package cssvarsassistant.documentation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil

/* ────────────────────────────────────────────────────────────────────────── */

data class ResolutionInfo(val original: String, val resolved: String, val steps: List<String> = emptyList())

private val LOG = Logger.getInstance("cssvarsassistant.docHelpers")

/* ────────────────────── resolver (uendret) ─────────────────────────────── */

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
                val entries = FileBasedIndex.getInstance()
                    .getValues(CSS_VARIABLE_INDEXER_NAME, ref, cssScope)
                    .flatMap { it.split(ENTRY_SEP) }
                    .filter { it.isNotBlank() }

                val defVal = entries.mapNotNull {
                    val p = it.split(DELIMITER, limit = 3)
                    if (p.size >= 2) p[0] to p[1] else null
                }.let { list ->
                    list.find { it.first == "default" }?.second ?: list.firstOrNull()?.second
                }

                if (defVal != null)
                    return resolveVarValue(project, defVal, visited + ref, depth + 1, newSteps)
            }
            return ResolutionInfo(raw, raw, steps)
        }

        val preprocessorMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())
        if (preprocessorMatch != null) {
            val varName = preprocessorMatch.groupValues[1]
            val prefix = if (raw.contains("@")) "@" else "$"
            val currentScope = ScopeUtil.currentPreprocessorScope(project)

            CssVarCompletionCache.get(project, varName, currentScope)?.let {
                return ResolutionInfo(raw, it, steps + "$prefix$varName")
            }

            val resolved = findPreprocessorVariableValue(project, varName)
            if (resolved != null && resolved != raw) {
                CssVarCompletionCache.put(project, varName, currentScope, resolved)
                return ResolutionInfo(raw, resolved, steps + "$prefix$varName")
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

private fun findPreprocessorVariableValue(project: Project, varName: String): String? =
    try {
        val freshScope = ScopeUtil.currentPreprocessorScope(project)
        PreprocessorUtil.resolveVariable(project, varName, freshScope)
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Failed to resolve @$varName", e)
        null
    }

/* ────────────────────── **ny, robust** ekstraktor ──────────────────────── */

fun extractCssVariableName(element: PsiElement): String? {
    // Always check element validity first
    if (!element.isValid) return null

    // Safe handling of potentially null text
    val elementText = element.text
    if (elementText?.trim()?.startsWith("--") == true) {
        return elementText.trim()
    }

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
                    val entries = FileBasedIndex.getInstance()
                        .getValues(CSS_VARIABLE_INDEXER_NAME, ref, cssScope)
                        .flatMap { it.split(ENTRY_SEP) }
                        .filter { it.isNotBlank() }

                    val defVal = entries.mapNotNull {
                        val p = it.split(DELIMITER, limit = 3)
                        if (p.size >= 2) p[0] to p[1] else null
                    }.let { list ->
                        list.find { it.first == "default" }?.second ?: list.firstOrNull()?.second
                    }

                    if (defVal != null) {
                        val childResolution = resolveVarValue(project, defVal, visited + ref, depth + 1, newSteps)
                        return ResolutionInfo(raw, childResolution.resolved, childResolution.steps)
                    }
                }
                return ResolutionInfo(raw, raw, steps)
            }

            // ... rest unchanged
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Error resolving variable value", e)
            return ResolutionInfo(raw, raw, steps)
        }
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

/* ─────────────────────── other helpers (uendret) ───────────────────────── */

fun lastLocalValueInFile(fileText: String, varName: String): String? =
    Regex("""\Q$varName\E\s*:\s*([^;]+);""")
        .findAll(fileText)
        .map { it.groupValues[1].trim() }
        .lastOrNull()
