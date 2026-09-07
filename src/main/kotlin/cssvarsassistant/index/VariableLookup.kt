package cssvarsassistant.index

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.settings.CssVarsAssistantSettings

data class VariableLocation(
    val file: VirtualFile?,
    val offset: Int = Int.MAX_VALUE,
    val namespace: String? = null,
    val cssContext: String? = null
)

internal data class SourcedCssValue(val file: VirtualFile, val value: IndexedCssVariableValue)
internal data class SourcedPreprocessorValue(val file: VirtualFile, val declaration: PreprocessorDeclaration)

/** Combines file-local indexes with explicitly imported files outside the indexable roots. */
internal object VariableLookup {
    private fun imports(project: Project, scope: GlobalSearchScope): List<VirtualFile> {
        val settings = CssVarsAssistantSettings.getInstance()
        if (!settings.shouldResolveImports) return emptyList()
        return ImportCache.get(project).getOrBuild(settings.maxImportDepth).filter { it.isValid && scope.contains(it) }
    }

    fun cssKeys(project: Project, scope: GlobalSearchScope): List<String> {
        val keys = linkedSetOf<String>()
        FileBasedIndex.getInstance().processAllKeys(CSS_VARIABLE_INDEXER_NAME, { key ->
            ProgressManager.checkCanceled()
            keys += key
            true
        }, scope, null)
        val indexed = keys.filter { FileBasedIndex.getInstance().getValues(CSS_VARIABLE_INDEXER_NAME, it, scope).isNotEmpty() }
        keys.clear()
        keys.addAll(indexed)
        for (file in imports(project, scope)) {
            ProgressManager.checkCanceled()
            StylesheetSnapshots.get(project).get(file)?.css?.forEach { keys += it.entry.name }
        }
        return keys.toList()
    }

    fun cssValues(project: Project, name: String, scope: GlobalSearchScope): List<SourcedCssValue> {
        val entries = linkedSetOf<SourcedCssValue>()
        FileBasedIndex.getInstance().processValues(CSS_VARIABLE_INDEXER_NAME, name, null, { file, packed ->
            CssVariableIndexValueCodec.decodePacked(packed).forEach { entries += SourcedCssValue(file, it) }
            true
        }, scope)
        for (file in imports(project, scope)) {
            ProgressManager.checkCanceled()
            val snapshot = StylesheetSnapshots.get(project).get(file) ?: continue
            snapshot.css.filter { it.entry.name == name }.forEach {
                val entry = it.entry
                entries += SourcedCssValue(file, IndexedCssVariableValue(entry.context, entry.value, entry.comment, entry.line, it.offset))
            }
        }
        return entries.toList()
    }

    fun preprocessorKeys(project: Project, scope: GlobalSearchScope): List<String> {
        val keys = linkedSetOf<String>()
        FileBasedIndex.getInstance().processAllKeys(PREPROCESSOR_VARIABLE_INDEX_NAME, { key ->
            ProgressManager.checkCanceled()
            keys += key
            true
        }, scope, null)
        for (file in imports(project, scope)) {
            ProgressManager.checkCanceled()
            StylesheetSnapshots.get(project).get(file)?.preprocessor?.declarations?.forEach { keys += canonicalPreprocessorName(it.name) }
        }
        return keys.toList()
    }

    fun preprocessorFiles(project: Project, name: String, scope: GlobalSearchScope): List<VirtualFile> {
        val files = linkedSetOf<VirtualFile>()
        files.addAll(FileBasedIndex.getInstance().getContainingFiles(PREPROCESSOR_VARIABLE_INDEX_NAME, canonicalPreprocessorName(name), scope))
        files.addAll(imports(project, scope).filter { file ->
            StylesheetSnapshots.get(project).get(file)?.preprocessor?.declarations?.any { canonicalPreprocessorName(it.name) == canonicalPreprocessorName(name) } == true
        })
        return files.toList()
    }

    fun preprocessorValues(project: Project, name: String, scope: GlobalSearchScope): List<SourcedPreprocessorValue> =
        preprocessorFiles(project, name, scope).mapNotNull { file ->
            val snapshot = StylesheetSnapshots.get(project).get(file) ?: return@mapNotNull null
            snapshot.preprocessor.visible(name, Int.MAX_VALUE, file.extension.equals("less", true))?.let { SourcedPreprocessorValue(file, it) }
        }
}
