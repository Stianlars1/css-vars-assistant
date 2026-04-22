package cssvarsassistant.index

import kotlin.test.Test
import kotlin.test.assertEquals

class CssVariableIndexValueCodecTest {

    // Phase 8b — the codec encodes a 4th field (line) into the packed record.
    // Round-tripping must preserve it so the hover popup can render
    // `file.css:42` for every indexed declaration.
    @Test
    fun `encodes and decodes full entry with line number round-trip`() {
        val encoded = CssVariableIndexValueCodec.encode(
            context = "[data-theme=\"dark\"]",
            value = "black",
            comment = "dark background",
            line = 42
        )

        val decoded = CssVariableIndexValueCodec.decodePacked(encoded).single()

        assertEquals("[data-theme=\"dark\"]", decoded.context)
        assertEquals("black", decoded.value)
        assertEquals("dark background", decoded.comment)
        assertEquals(42, decoded.line)
    }

    // Phase 8b — 1.8.0 and earlier wrote 3-part records (context|value|comment).
    // After the indexVersion bump the cache is rebuilt, so in production this
    // never happens. But the decoder must not crash on a stale record — it
    // returns `line = -1` as a sentinel that rendering code treats as "unknown".
    @Test
    fun `gracefully decodes legacy 3-part entries as line minus one`() {
        // Construct a legacy 3-part record without going through `encode`, so
        // this test locks in the on-disk compatibility contract even if the
        // internal encoder is refactored later.
        val legacyEncoded = "default" + DELIMITER + "white" + DELIMITER + "some comment"

        val decoded = CssVariableIndexValueCodec.decodePacked(legacyEncoded).single()

        assertEquals("default", decoded.context)
        assertEquals("white", decoded.value)
        assertEquals("some comment", decoded.comment)
        assertEquals(-1, decoded.line)
    }

    @Test
    fun `multiple entries joined by triple-pipe split correctly`() {
        val a = CssVariableIndexValueCodec.encode("default", "white", "", line = 2)
        val b = CssVariableIndexValueCodec.encode(".dark", "black", "", line = 5)
        val packed = a + ENTRY_SEPARATOR + b

        val decoded = CssVariableIndexValueCodec.decodePacked(packed)

        assertEquals(2, decoded.size)
        assertEquals(IndexedCssVariableValue("default", "white", "", 2), decoded[0])
        assertEquals(IndexedCssVariableValue(".dark", "black", "", 5), decoded[1])
    }

    // Defensive: empty comment + real line should still round-trip. The
    // common shape of a plain `:root { --bg: white; }` declaration.
    @Test
    fun `empty comment with line number round-trips`() {
        val encoded = CssVariableIndexValueCodec.encode("default", "white", "", line = 1)

        val decoded = CssVariableIndexValueCodec.decodePacked(encoded).single()

        assertEquals(IndexedCssVariableValue("default", "white", "", 1), decoded)
    }
}
