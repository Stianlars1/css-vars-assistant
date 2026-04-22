package cssvarsassistant.index

// ASCII unit-separator (0x1F). Invisible in editors but safe: nothing in
// valid CSS values, comments, or context labels uses this control character.
const val DELIMITER = ""
const val ENTRY_SEPARATOR = "|||"

data class IndexedCssVariableValue(
    val context: String,
    val value: String,
    val comment: String,
    // 1-based source line of the declaration, or `-1` if unknown (legacy
    // 3-part cache records from 1.8.0 and earlier).
    val line: Int
)

object CssVariableIndexValueCodec {

    fun encode(context: String, value: String, comment: String, line: Int): String =
        "$context$DELIMITER$value$DELIMITER$comment$DELIMITER$line"

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
        // Split with limit = 4 so a value or comment that somehow contains
        // our internal delimiter cannot overflow into the line field.
        val parts = packedEntry.split(DELIMITER, limit = 4)
        if (parts.size < 2) {
            return null
        }

        return IndexedCssVariableValue(
            context = parts[0],
            value = parts[1],
            comment = parts.getOrElse(2) { "" },
            // Legacy 3-part records decode with line = -1 so renderers can
            // fall back to the existing "first resolution step" behaviour.
            line = parts.getOrNull(3)?.toIntOrNull() ?: -1
        )
    }
}
