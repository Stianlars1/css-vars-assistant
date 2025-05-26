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
import org.apache.log4j.Logger
import java.io.DataInput
import java.io.DataOutput

const val DELIMITER = "\u001F"
private const val ENTRY_SEP = "|||"

class CssVariableIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        val NAME = ID.create<String, String>("cssvarsassistant.index")
    }

    private val logger = Logger.getLogger(CssVariableIndex::class.java)

    override fun getName(): ID<String, String> = NAME
    override fun getVersion(): Int = 8  // Increment for LESS support

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
            // Determine file type
            val isLessFile = inputData.file.extension?.lowercase().equals("less", true)


            // Index the current file
            indexFileContent(inputData.contentAsText, map, isLessFile)

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
        val realProject = project
            ?: com.intellij.openapi.project.ProjectLocator.getInstance().guessProjectForFile(file)
            ?: return                           // fortsatt ingenting vi kan gjøre

        try {
            val importedFiles = ImportResolver.resolveImports(
                file,
                realProject,
                settings.maxImportDepth
            )

            for (imported in importedFiles) {
                try {
                    val content = String(imported.contentsToByteArray())
                    indexFileContent(content, map, imported.extension.equals("less", true))
                } catch (e: Exception) {
                    logger.debug("Error indexing ${imported.path}", e)
                }
            }
        } catch (e: Exception) {
            logger.debug("Error resolving imports for ${file.path}", e)
        }
    }

    /**
     * Indexes CSS variable declarations from file content
     * Now supports both CSS custom properties (--var) and LESS variables (@var)
     */
    private fun indexFileContent(text: CharSequence, map: MutableMap<String, String>, isLessFile: Boolean) {
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

            // ALWAYS index CSS custom properties (--variables) regardless of file type
            val cssVarDecl = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*([^;]+);""").find(line)
            if (cssVarDecl != null) {
                val varName = cssVarDecl.groupValues[1]
                val value = cssVarDecl.groupValues[2].trim()
                val comment = lastComment ?: ""
                val entry = "$currentContext$DELIMITER$value$DELIMITER$comment"
                val prev = map[varName]
                map[varName] = if (prev == null) entry else prev + ENTRY_SEP + entry
                lastComment = null
            }

            // Also index LESS variables (@var) in LESS files, converting them to CSS custom property format
            if (isLessFile) {
                val lessVarDecl = Regex("""(@[A-Za-z0-9\-_]+)\s*:\s*([^;]+);""").find(line)
                if (lessVarDecl != null && !line.startsWith("@import") && !line.startsWith("@media")) {
                    val varName = lessVarDecl.groupValues[1]
                    val value = lessVarDecl.groupValues[2].trim()
                    val comment = lastComment ?: ""

                    // Convert LESS variable to CSS custom property format for consistency
                    val cssVarName = "--" + varName.substring(1)
                    val entry = "$currentContext$DELIMITER$value$DELIMITER$comment$DELIMITER#LESS"

                    val prev = map[cssVarName]
                    map[cssVarName] = if (prev == null) entry else prev + ENTRY_SEP + entry
                    lastComment = null
                }
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