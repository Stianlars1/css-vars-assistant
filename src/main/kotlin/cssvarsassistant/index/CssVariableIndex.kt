package cssvarsassistant.index

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

val CSS_VARIABLE_INDEXER_NAME = ID.create<String, String>("cssvarsassistant.index")

class CssVariableIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = CSS_VARIABLE_INDEXER_NAME
    override fun getVersion(): Int = INDEX_VERSION
    override fun dependsOnFileContent(): Boolean = true

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        val registry = FileTypeRegistry.getInstance()
        val types = listOf("css", "scss", "sass", "less").map(registry::getFileTypeByExtension).filter { it != UnknownFileType.INSTANCE }.toSet()
        return FileBasedIndex.InputFilter { it.fileType in types }
    }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { input ->
        CssVariableEntryParser.declarations(input.contentAsText, input.file.extension?.lowercase())
            .groupBy { it.entry.name }
            .mapValues { (_, entries) ->
                entries.joinToString(ENTRY_SEPARATOR) { CssVariableIndexValueCodec.encode(it.entry.context, it.entry.value, it.entry.comment, it.entry.line, it.offset) }
            }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = object : DataExternalizer<String> {
        override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
        override fun read(input: DataInput): String = IOUtil.readUTF(input)
    }
}
