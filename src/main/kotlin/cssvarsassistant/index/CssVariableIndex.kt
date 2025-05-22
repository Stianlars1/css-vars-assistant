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

class CssVariableIndex : FileBasedIndexExtension<String, String>() {
    companion object {
        val NAME = ID.create<String, String>("cssvarsassistant.index")
    }

    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(CssVariableIndex::class.java)

    override fun getName(): ID<String, String> = NAME
    override fun getVersion(): Int = 8

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

            if (!isStylesheetFile) {
                return@InputFilter false
            }

            val settings = CssVarsAssistantSettings.getInstance()
            val shouldIndex = shouldIndexFile(virtualFile, settings)

            LOG.debug("🔍 FILTER CHECK: ${virtualFile.path}")
            LOG.debug("  Extension: ${virtualFile.extension}")
            LOG.debug("  FileType: ${fileType.name}")
            LOG.debug("  ShouldIndex: $shouldIndex")

            shouldIndex
        }
    }

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { inputData ->
        val map = mutableMapOf<String, String>()
        val settings = CssVarsAssistantSettings.getInstance()

        try {
            LOG.info("🚀 INDEXING START: ${inputData.file.path}")
            LOG.info("  Extension: ${inputData.file.extension}")
            LOG.info("  Indexing scope: ${settings.indexingScope}")
            LOG.info("  Should resolve imports: ${settings.shouldResolveImports}")
            LOG.info("  Max import depth: ${settings.maxImportDepth}")

            // Index the main file
            val mainFileVars = indexFileContent(inputData.contentAsText, "MAIN FILE")
            map.putAll(mainFileVars)
            LOG.info("📊 Found ${mainFileVars.size} variables in main file")

            // Index imported files if enabled
            if (settings.shouldResolveImports) {
                LOG.info("🔗 Starting import resolution...")
                val importVars = indexImportedFiles(inputData.file, inputData.project, settings)
                val beforeTotal = map.size
                map.putAll(importVars)
                val afterTotal = map.size
                LOG.info("📊 Added ${afterTotal - beforeTotal} variables from imports (${importVars.size} total found)")

                if (importVars.isNotEmpty()) {
                    LOG.info("📝 Import variables found:")
                    importVars.keys.take(10).forEach { key ->
                        LOG.info("  • $key")
                    }
                    if (importVars.size > 10) {
                        LOG.info("  ... and ${importVars.size - 10} more")
                    }
                }
            }

            LOG.info("✅ INDEXING COMPLETE: ${map.size} total variables for ${inputData.file.name}")

        } catch (e: Exception) {
            LOG.error("💥 Error indexing file ${inputData.file.path}", e)
        }

        map
    }

    private fun shouldIndexFile(file: VirtualFile, settings: CssVarsAssistantSettings): Boolean {
        return when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> true
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY -> !file.path.contains("/node_modules/")
            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> !file.path.contains("/node_modules/")
        }
    }

    private fun indexImportedFiles(
        file: VirtualFile,
        project: Project?,
        settings: CssVarsAssistantSettings
    ): MutableMap<String, String> {
        val importMap = mutableMapOf<String, String>()

        if (project == null) {
            LOG.warn("⚠️ No project available for import resolution")
            return importMap
        }

        try {
            LOG.info("🔍 Resolving imports for: ${file.path}")
            val importedFiles = ImportResolver.resolveImports(
                file,
                project,
                settings.maxImportDepth
            )

            LOG.info("📦 Processing ${importedFiles.size} imported files:")
            importedFiles.forEachIndexed { index, importedFile ->
                LOG.info("  ${index + 1}. ${importedFile.path}")
            }

            for ((index, importedFile) in importedFiles.withIndex()) {
                try {
                    LOG.info("📄 [${index + 1}/${importedFiles.size}] Processing: ${importedFile.name}")
                    val importedContent = String(importedFile.contentsToByteArray())
                    val fileVars = indexFileContent(importedContent, importedFile.name)

                    val beforeCount = importMap.size
                    importMap.putAll(fileVars)
                    val afterCount = importMap.size
                    val addedCount = afterCount - beforeCount

                    LOG.info("  ✅ Added $addedCount variables from ${importedFile.name} (${fileVars.size} found)")

                    if (fileVars.isNotEmpty()) {
                        fileVars.keys.take(5).forEach { key ->
                            LOG.info("    • $key")
                        }
                        if (fileVars.size > 5) {
                            LOG.info("    ... and ${fileVars.size - 5} more")
                        }
                    }

                } catch (e: Exception) {
                    LOG.error("💥 Error indexing imported file ${importedFile.path}", e)
                }
            }

        } catch (e: Exception) {
            LOG.error("💥 Error resolving imports for ${file.path}", e)
        }

        return importMap
    }

    private fun indexFileContent(text: CharSequence, sourceName: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = text.lines()
        var currentContext = "default"
        val contextStack = ArrayDeque<String>()
        var lastComment: String? = null
        var inBlockComment = false
        val blockComment = StringBuilder()

        LOG.debug("🔍 Scanning $sourceName (${lines.size} lines)")

        for ((lineNum, rawLine) in lines.withIndex()) {
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
                        line.removePrefix("/**").removePrefix("/*").removeSuffix("*/").trim()
                    )
                    lastComment = blockComment.toString().trim()
                    inBlockComment = false
                    continue
                } else {
                    blockComment.append(line.removePrefix("/**").removePrefix("/*").trim())
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

            // CSS Variable Extraction
            val varDecl = Regex("""(--[A-Za-z0-9\-_]+)\s*:\s*([^;]+);""").find(line)
            if (varDecl != null) {
                val varName = varDecl.groupValues[1]
                val value = varDecl.groupValues[2].trim()
                val comment = lastComment ?: ""
                val entry = "$currentContext$DELIMITER$value$DELIMITER$comment"
                val prev = map[varName]
                map[varName] = if (prev == null) entry else prev + ENTRY_SEP + entry
                lastComment = null

                LOG.debug("  ✅ Variable found in $sourceName:${lineNum + 1}: $varName = $value")
            }
        }

        LOG.debug("📊 Total variables found in $sourceName: ${map.size}")
        return map
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> =
        object : DataExternalizer<String> {
            override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
            override fun read(`in`: DataInput): String = IOUtil.readUTF(`in`)
        }
}