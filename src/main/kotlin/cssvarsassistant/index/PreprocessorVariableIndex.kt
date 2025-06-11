package cssvarsassistant.index

import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

val PREPROCESSOR_VARIABLE_INDEX_NAME: ID<String, String> =
    ID.create("cssvarsassistant.preprocessor.index")

class PreprocessorVariableIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = PREPROCESSOR_VARIABLE_INDEX_NAME
    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        val exts = setOf("scss", "sass", "less")
        return FileBasedIndex.InputFilter { file ->
            val ext = file.extension?.lowercase()
            ext in exts
        }
    }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { input ->
        val map = mutableMapOf<String, String>()
        val text = input.contentAsText
        val regexes = listOf(
            Regex("@([\\w-]+)\\s*:\\s*([^;]+);"),
            Regex("\\$([\\w-]+)\\s*:\\s*([^;]+);")
        )
        for (regex in regexes) {
            regex.findAll(text).forEach { m ->
                val name = m.groupValues[1]
                val value = m.groupValues[2].trim()
                map[name] = value
            }
        }
        map
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = object : DataExternalizer<String> {
        override fun save(out: DataOutput, value: String) = IOUtil.writeUTF(out, value)
        override fun read(`in`: DataInput): String = IOUtil.readUTF(`in`)
    }
}
