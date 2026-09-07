package cssvarsassistant.documentation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/* ───────────────────────── Dochelper ─────────────────────────────────────────────── */

data class ResolutionInfo(val original: String, val resolved: String, val steps: List<String> = emptyList())


/* ────────────────────── resolver ─────────────────────────────── */
fun resolveVarValue(
    project: Project,
    raw: String,
    visited: Set<String> = emptySet(),
    depth: Int = 0,
    steps: List<String> = emptyList()
): ResolutionInfo = VariableResolver(project).resolve(raw, visited = visited, depth = depth, steps = steps)

fun findPreprocessorVariableValue(
    project: Project,
    varName: String,
    currentSteps: List<String> = emptyList()
): ResolutionInfo? = VariableResolver(project).resolve(varName, steps = currentSteps)

/* ────────────────────── ** extractor ──────────────────────── */

fun extractCssVariableName(element: PsiElement): String? = VariablePsiContext.variableName(element)

/* ─────────────────────── other helpers ───────────────────────── */
// Returns the last `--varName: value;` declaration in the file, IGNORING
// matches that appear inside /* ... */ or // ... comments. Issue #18 Bug A:
// prior versions scanned raw text and picked up commented-out example
// declarations as if they were real overrides, so the popup/hover showed
// the wrong value. Comments are replaced with whitespace first so we still
// respect token boundaries.
fun lastLocalValueInFile(fileText: String, varName: String): String? =
    cssvarsassistant.index.CssVariableEntryParser.parse(fileText)
        .lastOrNull { it.name == varName }?.value


private val PURE_CSS_VAR_ALIAS = Regex(
    """^\s*var\(\s*(--[\w-]+)\s*(?:,[^()]*)?\)\s*;?\s*$"""
)

fun extractCssVarAlias(rawValue: String): String? =
    PURE_CSS_VAR_ALIAS.find(rawValue)?.groupValues?.get(1)
