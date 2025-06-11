package cssvarsassistant.index

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CompletionDataCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.PreprocessorUtil
import java.io.DataInput
import java.io.DataOutput

const val DELIMITER = "\u001F"
private const val ENTRY_SEP = "|||"

val CSS_VARIABLE_INDEXER_NAME = ID.create<String, String>("cssvarsassistant.index")

class CssVariableIndex : FileBasedIndexExtension<String, String>() {

    override fun getName(): ID<String, String> = CSS_VARIABLE_INDEXER_NAME
    override fun getVersion(): Int = 203  // PERFORMANCE FIX: Increment due to preprocessor indexing changes

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

            val settings = CssVarsAssistantSettings.getInstance()
            shouldIndexFile(virtualFile, settings)
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { inputData ->
        val map = mutableMapOf<String, String>()
        val settings = CssVarsAssistantSettings.getInstance()

        try {
            // Index the current file (CSS variables)
            indexFileContent(inputData.contentAsText, map)

            // PERFORMANCE FIX: Pre-index preprocessor variables during indexing
            // This eliminates the need for file I/O during completion
            indexPreprocessorVariables(inputData.contentAsText, map)

            // If import resolution is enabled, also index imported files
            if (settings.shouldResolveImports) {
                indexImportedFiles(inputData.file, inputData.project, settings, map)
            }
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
                !file.path.contains("/node_modules/")
            }

            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> {
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

            ImportCache.get(project).add(project, importedFiles)

            // PERFORMANCE FIX: Clear caches when imports change
            PreprocessorUtil.clearCache()
            CssVarCompletionCache.clearCaches()
            CompletionDataCache.get(project).clear()

            for (importedFile in importedFiles) {
                try {
                    val importedContent = String(importedFile.contentsToByteArray())
                    indexFileContent(importedContent, map)
                    // PERFORMANCE FIX: Also index preprocessor variables from imported files
                    indexPreprocessorVariables(importedContent, map)
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
        val lines = text.lines()
        var currentContext = "default"
        val contextStack = ArrayDeque<String>()

        var lastComment: String? = null
        var inBlockComment = false
        val blockComment = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trim()

            // Media Query Context Handling
            if (line.startsWith("@media")) {
                val m = Regex("""@media\s*\(([^)]+)\)""").find(line)
                val mediaLabel = m?.groupValues?.get(1)?.trim() ?: "media"
                contextStack.addLast(mediaLabel)
                currentContext = contextStack.last()
                continue
            }
            if (line == "}") {
                if (contextStack.isNotEmpty()) {
                    contextStack.removeLast()
                    currentContext = contextStack.lastOrNull() ?: "default"
                }
                continue
            }

            // Comment Extraction
            if (!inBlockComment && (line.startsWith("/*") || line.startsWith("/**"))) {
                inBlockComment = true
                blockComment.clear()
                if (line.contains("*/")) {
                    blockComment.append(
                        line
                            .removePrefix("/**").removePrefix("/*")
                            .removeSuffix("*/").trim()
                    )
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                    continue
                } else {
                    blockComment.append(
                        line
                            .removePrefix("/**").removePrefix("/*").trim()
                    )
                    continue
                }
            }
            if (inBlockComment) {
                if (line.contains("*/")) {
                    blockComment.append("\n" + line.removeSuffix("*/"))
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                } else {
                    blockComment.append("\n" + line)
                }
                continue
            }

            // Variable Extraction
            val varDecl = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*([^;]+);""").find(line)
            if (varDecl != null) {
                val varName = varDecl.groupValues[1]
                val value = varDecl.groupValues[2].trim()
                val comment = lastComment ?: ""
                val entry = "$currentContext$DELIMITER$value$DELIMITER$comment"
                val prev = map[varName]
                map[varName] = if (prev == null) entry else prev + ENTRY_SEP + entry
                lastComment = null
            }
        }
    }

    /**
     * PERFORMANCE FIX: Pre-index preprocessor variables to avoid runtime file I/O
     * This method extracts LESS and SCSS variables during indexing so they don't
     * need to be resolved by scanning files during completion
     */
    private fun indexPreprocessorVariables(
        text: CharSequence,
        map: MutableMap<String, String>
    ) {
        val lines = text.lines()

        for (line in lines) {
            val trimmedLine = line.trim()

            // LESS variables: @variable: value;
            val lessMatch = Regex("""@([\w-]+)\s*:\s*([^;]+);""").find(trimmedLine)
            if (lessMatch != null) {
                val varName = "@${lessMatch.groupValues[1]}"
                val value = lessMatch.groupValues[2].trim()
                val entry = "default${DELIMITER}${value}${DELIMITER}"
                val prev = map[varName]
                map[varName] = if (prev == null) entry else prev + ENTRY_SEP + entry
            }

            // SCSS variables: $variable: value;
            val scssMatch = Regex("""\$([\w-]+)\s*:\s*([^;]+);""").find(trimmedLine)
            if (scssMatch != null) {
                val varName = "\$${scssMatch.groupValues[1]}"
                val value = scssMatch.groupValues[2].trim()
                val entry = "default${DELIMITER}${value}${DELIMITER}"
                val prev = map[varName]
                map[varName] = if (prev == null) entry else prev + ENTRY_SEP + entry
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