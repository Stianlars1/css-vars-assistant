package cssvarsassistant.index

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

const val DELIMITER = "\u001f"
const val ENTRY_SEPARATOR = "|||"

data class IndexedCssVariableValue @JvmOverloads constructor(val context: String, val value: String, val comment: String, val line: Int, val offset: Int = -1)

object CssVariableIndexValueCodec {
    private const val PREFIX = "CVA2:"

    fun encode(context: String, value: String, comment: String, line: Int): String = encode(context, value, comment, line, -1)

    fun encode(context: String, value: String, comment: String, line: Int, offset: Int): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(line)
            output.writeInt(offset)
            for (field in listOf(context, value, comment)) {
                val encoded = field.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(entries: Collection<String>): List<IndexedCssVariableValue> = entries.flatMap(::decodePacked)

    fun decodePacked(packedEntries: String): List<IndexedCssVariableValue> =
        packedEntries.split(ENTRY_SEPARATOR).filter(String::isNotBlank).mapNotNull(::decodeSingle)

    private fun decodeSingle(packedEntry: String): IndexedCssVariableValue? {
        if (!packedEntry.startsWith(PREFIX)) {
            val parts = packedEntry.split(DELIMITER, limit = 4)
            if (parts.size < 2) return null
            return IndexedCssVariableValue(parts[0], parts[1], parts.getOrElse(2) { "" }, parts.getOrNull(3)?.toIntOrNull() ?: -1)
        }
        return try {
            DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(packedEntry.removePrefix(PREFIX)))).use { input ->
                val line = input.readInt()
                val offset = input.readInt()
                val fields = (0 until 3).map {
                    val length = input.readInt()
                    if (length < 0 || length > input.available()) return null
                    ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
                }
                if (input.available() != 0) return null
                IndexedCssVariableValue(fields[0], fields[1], fields[2], line, offset)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }
}
