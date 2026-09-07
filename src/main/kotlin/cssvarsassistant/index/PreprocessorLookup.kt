package cssvarsassistant.index

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import cssvarsassistant.settings.CssVarsAssistantSettings

/** Resolves declarations at their use site; expression evaluation is a separate step. */
internal class PreprocessorLookup(private val project: Project, private val scope: GlobalSearchScope) {
    private val imports = mutableMapOf<VirtualFile, List<ImportResolver.ResolvedImport>>()
    private val depthLimit = CssVarsAssistantSettings.getInstance().maxImportDepth

    fun find(name: String, location: VariableLocation?): SourcedPreprocessorValue? {
        val separator = name.lastIndexOf(".$")
        val namespace = if (separator >= 0) name.substring(0, separator) else location?.namespace
        val key = if (separator >= 0) name.substring(separator + 1) else name
        val file = location?.file
        if (file != null) {
            findInFile(file, key, location.offset, namespace, emptySet(), 0)?.let { return it }
            if (namespace != null || directives(file).any { it.kind == "use" }) return null
        }
        val candidates = VariableLookup.preprocessorValues(project, key, scope)
        if (candidates.map { it.declaration.value }.distinct().size <= 1) return candidates.firstOrNull()
        return candidates.singleOrNull { candidate ->
            val dependencies = ImportResolver.resolveImports(candidate.file, project, depthLimit)
            candidates.all { it.file == candidate.file || it.file in dependencies }
        }
    }

    private fun directives(file: VirtualFile): List<ImportResolver.ResolvedImport> =
        imports.getOrPut(file) { ImportResolver.resolveDirectImports(file, project) }

    private fun findInFile(
        file: VirtualFile,
        name: String,
        offset: Int,
        namespace: String?,
        path: Set<VirtualFile>,
        depth: Int
    ): SourcedPreprocessorValue? {
        ProgressManager.checkCanceled()
        if (!file.isValid || file in path || depth > depthLimit) return null
        val snapshot = StylesheetSnapshots.get(project).get(file) ?: return null
        val less = file.extension.equals("less", true)
        val activeScope = snapshot.preprocessor.scopeAt(offset)
        val local = if (namespace == null) snapshot.preprocessor.named(name).filter { less || it.offset < offset } else emptyList()
        val imported = directives(file).filter { less || it.offset < offset }
        for (level in activeScope.size downTo 0) {
            val lexicalScope = activeScope.take(level)
            val bindings = local.filter { it.scope == lexicalScope }.map { Binding(it.offset, declaration = it) } +
                imported.filter { snapshot.preprocessor.scopeAt(it.offset) == lexicalScope }.map { Binding(it.offset, directive = it) }
            var selected: SourcedPreprocessorValue? = null
            for (binding in bindings.sortedBy { it.offset }) {
                binding.declaration?.let { declaration ->
                    if (!declaration.defaultOnly || selected == null || selected?.declaration?.value == "null") selected = SourcedPreprocessorValue(file, declaration)
                }
                val directive = binding.directive ?: continue
                if (namespace != null) {
                    if (directive.kind != "use" || canonicalPreprocessorName("$" + directive.namespace) != canonicalPreprocessorName("$" + namespace)) continue
                } else if (directive.kind == "use" && directive.namespace != "*") continue
                if (directive.kind in setOf("use", "forward") && name.drop(1).firstOrNull() in setOf('_', '-')) continue
                for (dependency in directive.resolvedFiles) {
                    if (!scope.contains(dependency)) continue
                    val candidate = findInFile(dependency, name, Int.MAX_VALUE, null, path + file, depth + 1) ?: continue
                    if (directive.kind != "import" || !candidate.declaration.defaultOnly || selected == null || selected?.declaration?.value == "null") selected = candidate
                }
            }
            if (selected != null) return selected
        }
        return null
    }

    private data class Binding(
        val offset: Int,
        val declaration: PreprocessorDeclaration? = null,
        val directive: ImportResolver.ResolvedImport? = null
    )
}
