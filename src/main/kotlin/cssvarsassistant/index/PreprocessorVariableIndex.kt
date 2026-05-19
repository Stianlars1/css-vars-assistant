package cssvarsassistant.index

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.CssTextUtil
import java.io.DataInput
import java.io.DataOutput

val PREPROCESSOR_VARIABLE_INDEX_NAME: ID<String, String> =
    ID.create("cssvarsassistant.preprocessor.index")

class PreprocessorVariableIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = PREPROCESSOR_VARIABLE_INDEX_NAME
    override fun getVersion(): Int = INDEX_VERSION


    override fun dependsOnFileContent(): Boolean = true

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        val exts = setOf("scss", "sass", "less")
        return FileBasedIndex.InputFilter { file ->
            val ext = file.extension?.lowercase()
            if (ext !in exts) {
                return@InputFilter false
            }

            val settings = CssVarsAssistantSettings.getInstance()
            if (settings.indexingScope != CssVarsAssistantSettings.IndexingScope.GLOBAL) {
                return@InputFilter !file.path.contains("/node_modules/")
            }

            true
        }
    }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { input ->
        val map = linkedMapOf<String, String>()
        val settings = CssVarsAssistantSettings.getInstance()

        if (settings.shouldResolveImports) {
            indexImportedFiles(input, settings, map)
        }

        map.putAll(PreprocessorVariableEntryParser.parse(input.contentAsText, input.file.extension?.lowercase()))
        map
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = object : DataExternalizer<String> {
        override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
        override fun read(`in`: DataInput): String = IOUtil.readUTF(`in`)
    }

    private fun indexImportedFiles(
        input: FileContent,
        settings: CssVarsAssistantSettings,
        map: MutableMap<String, String>
    ) {
        val project = input.project ?: return
        try {
            ImportResolver.resolveImports(input.file, project, settings.maxImportDepth).forEach { importedFile ->
                try {
                    map.putAll(
                        PreprocessorVariableEntryParser.parse(
                            String(importedFile.contentsToByteArray()),
                            importedFile.extension?.lowercase()
                        )
                    )
                } catch (e: Exception) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(PreprocessorVariableIndex::class.java)
                        .debug("Error indexing imported preprocessor file ${importedFile.path}", e)
                }
            }
        } catch (e: Exception) {
            com.intellij.openapi.diagnostic.Logger.getInstance(PreprocessorVariableIndex::class.java)
                .debug("Error resolving preprocessor imports for ${input.file.path}", e)
        }
    }
}

internal object PreprocessorVariableEntryParser {
    private val semicolonDeclaration = Regex(
        """(?s)([$@])([\w-]+)\s*:\s*(.*?);"""
    )
    private val sassLineDeclaration = Regex(
        """(?m)^\s*(\$)([\w-]+)\s*:\s*([^\r\n]+)$"""
    )
    private val trailingFlags = Regex("""\s+!(?:default|global)\b""", RegexOption.IGNORE_CASE)

    fun parse(text: CharSequence, extension: String?): Map<String, String> {
        val cleaned = CssTextUtil.stripCssComments(text.toString())
        val map = linkedMapOf<String, String>()
        val ext = extension?.lowercase()

        if (ext == "sass") {
            sassLineDeclaration.findAll(cleaned).forEach { match ->
                putEntry(map, match.groupValues[1], match.groupValues[2], match.groupValues[3])
            }
        }

        semicolonDeclaration.findAll(cleaned).forEach { match ->
            val symbol = match.groupValues[1]
            if (symbol == "@" && ext != "less") return@forEach
            if (symbol == "$" && ext == "less") return@forEach
            putEntry(map, symbol, match.groupValues[2], match.groupValues[3])
        }

        return map
    }

    private fun putEntry(
        map: MutableMap<String, String>,
        symbol: String,
        name: String,
        rawValue: String
    ) {
        val value = rawValue
            .replace(trailingFlags, "")
            .trim()

        if (value.isNotBlank()) {
            map["$symbol$name"] = value
        }
    }
}
