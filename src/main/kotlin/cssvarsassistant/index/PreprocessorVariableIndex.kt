package cssvarsassistant.index

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

val PREPROCESSOR_VARIABLE_INDEX_NAME: ID<String, String> = ID.create("cssvarsassistant.preprocessor.index")

class PreprocessorVariableIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = PREPROCESSOR_VARIABLE_INDEX_NAME
    override fun getVersion(): Int = INDEX_VERSION
    override fun dependsOnFileContent(): Boolean = true
    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { it.extension?.lowercase() in setOf("scss", "sass", "less") }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { input ->
        PreprocessorVariableEntryParser.parse(input.contentAsText, input.file.extension?.lowercase())
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = object : DataExternalizer<String> {
        override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
        override fun read(input: DataInput): String = IOUtil.readUTF(input)
    }
}
