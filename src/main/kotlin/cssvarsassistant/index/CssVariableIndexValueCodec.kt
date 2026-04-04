package cssvarsassistant.index

const val DELIMITER = "\u001F"
const val ENTRY_SEPARATOR = "|||"

data class IndexedCssVariableValue(
    val context: String,
    val value: String,
    val comment: String
)

object CssVariableIndexValueCodec {

    fun encode(context: String, value: String, comment: String): String =
        "$context$DELIMITER$value$DELIMITER$comment"

    fun decode(entries: Collection<String>): List<IndexedCssVariableValue> =
        entries.asSequence()
            .flatMap { decodePacked(it).asSequence() }
            .toList()

    fun decodePacked(packedEntries: String): List<IndexedCssVariableValue> =
        packedEntries
            .split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull(::decodeSingle)

    private fun decodeSingle(packedEntry: String): IndexedCssVariableValue? {
        val parts = packedEntry.split(DELIMITER, limit = 3)
        if (parts.size < 2) {
            return null
        }

        return IndexedCssVariableValue(
            context = parts[0],
            value = parts[1],
            comment = parts.getOrElse(2) { "" }
        )
    }
}
