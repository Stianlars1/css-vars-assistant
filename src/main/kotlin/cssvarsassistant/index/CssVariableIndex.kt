package cssvarsassistant.index

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import cssvarsassistant.settings.CssVarsAssistantSettings
import java.io.DataInput
import java.io.DataOutput

const val DELIMITER = "\u001F"
private const val ENTRY_SEP = "|||"

val CSS_VARIABLE_INDEXER_NAME = ID.create<String, String>("cssvarsassistant.index")

class CssVariableIndex : FileBasedIndexExtension<String, String>() {

    override fun getName(): ID<String, String> = CSS_VARIABLE_INDEXER_NAME
    override fun getVersion(): Int = INDEX_VERSION

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        val registry = FileTypeRegistry.getInstance()
        val cssFileType = registry.getFileTypeByExtension("css")
        val scssFileType = registry.getFileTypeByExtension("scss")
        val lessFileType = registry.getFileTypeByExtension("less")
        val sassFileType = registry.getFileTypeByExtension("sass")

        return FileBasedIndex.InputFilter { virtualFile ->
            val fileType = virtualFile.fileType
            val isStylesheetFile = fileType == cssFileType || fileType == scssFileType ||
                    fileType == lessFileType || fileType == sassFileType

            if (!isStylesheetFile) return@InputFilter false

            // Get settings to determine if this file should be indexed
            val settings = CssVarsAssistantSettings.getInstance()
            shouldIndexFile(virtualFile, settings)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { inputData ->
        val map = mutableMapOf<String, String>()
        val settings = CssVarsAssistantSettings.getInstance()

        try {
            // If import resolution is enabled, also index imported files
            if (settings.shouldResolveImports) {
                indexImportedFiles(inputData.file, inputData.project, settings, map)
            }
            // Index the current file
            indexFileContent(inputData.contentAsText, map)

        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(CssVariableIndex::class.java)
                .debug("Error indexing file ${inputData.file.path}", e)
        }

        map
    }

    /**
     * Determines if a file should be indexed based on current settings
     */
    private fun shouldIndexFile(file: VirtualFile, settings: CssVarsAssistantSettings): Boolean {
        return when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> true

            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY -> {
                // Only index files within the project, not in node_modules
                !file.path.contains("/node_modules/")
            }

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
                // Index project files and selectively imported external files
                // This is handled by the import resolution logic
                !file.path.contains("/node_modules/")
            }
        }
    }

    /**
     * Indexes imported files when import resolution is enabled
     */
    private fun indexImportedFiles(
        file: VirtualFile,
        project: Project?,
        settings: CssVarsAssistantSettings,
        map: MutableMap<String, String>
    ) {
        if (project == null) return

        try {
            val importedFiles = ImportResolver.resolveImports(
                file,
                project,
                settings.maxImportDepth,
                currentDepth = 0
            )
            for (importedFile in importedFiles) {
                try {
                    val importedContent = String(importedFile.contentsToByteArray())
                    indexFileContent(importedContent, map)
                } catch (e: Exception) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(CssVariableIndex::class.java)
                        .debug("Error indexing imported file ${importedFile.path}", e)
                }
            }
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(CssVariableIndex::class.java)
                .debug("Error resolving imports for ${file.path}", e)
        }
    }

    /**
     * Indexes CSS variable declarations from file content
     */
    private fun indexFileContent(text: CharSequence, map: MutableMap<String, String>) {
        CssVariableEntryParser.parse(text).forEach { parsedEntry ->
            val entry = "${parsedEntry.context}$DELIMITER${parsedEntry.value}$DELIMITER${parsedEntry.comment}"

            // PRIORITY LOGIC: Direct values (non-preprocessor) win
            val isPreprocessorRef = parsedEntry.value.matches(Regex("""[@$][\w-]+"""))
            val prev = map[parsedEntry.name]

            if (prev == null) {
                map[parsedEntry.name] = entry
            } else {
                // If current value is direct and previous was preprocessor, override
                val prevEntries = prev.split(ENTRY_SEP)
                val hasDirectValue = prevEntries.any { prevEntry ->
                    val prevValue = prevEntry.split(DELIMITER).getOrNull(1) ?: ""
                    !prevValue.matches(Regex("""[@$][\w-]+"""))
                }

                if (!isPreprocessorRef || !hasDirectValue) {
                    map[parsedEntry.name] = prev + ENTRY_SEP + entry
                }
                // else: skip preprocessor reference if we already have direct values
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> =
        object : DataExternalizer<String> {
            override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
            override fun read(`in`: DataInput): String = IOUtil.readUTF(`in`)
        }
}
