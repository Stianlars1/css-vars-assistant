package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatSourceCellTest {

    // 1.8.2 — compact mode renders just `:line` in the cell so the popup
    // doesn't wrap at IntelliJ's max-width clamp. The caller pairs this with
    // a tooltip that shows the full `file.css:line` string.
    @Test
    fun `compact mode renders only the colon-prefixed line number`() {
        assertEquals(
            ":220",
            formatSourceCell(file = "variables.css", line = 220, firstStep = null, compact = true)
        )
    }

    @Test
    fun `full mode renders file colon line`() {
        assertEquals(
            "variables.css:220",
            formatSourceCell(file = "variables.css", line = 220, firstStep = null, compact = false)
        )
    }

    // Legacy 3-part index records from 1.8.0 decode with `line = -1`; both
    // compact and full mode fall back to the resolution-chain step rather
    // than render a nonsense `:−1` string.
    @Test
    fun `legacy record with unknown line falls back to first step in compact mode`() {
        assertEquals(
            "calc(var(--base) * 2)",
            formatSourceCell(file = "variables.css", line = -1, firstStep = "calc(var(--base) * 2)", compact = true)
        )
    }

    @Test
    fun `legacy record with unknown line falls back to first step in full mode`() {
        assertEquals(
            "calc(var(--base) * 2)",
            formatSourceCell(file = "variables.css", line = -1, firstStep = "calc(var(--base) * 2)", compact = false)
        )
    }

    // Synthesised rows with neither line info nor a resolution step render
    // an em-dash sentinel in both modes — same as 1.8.1.
    @Test
    fun `no source and no step renders em-dash in both modes`() {
        assertEquals("—", formatSourceCell(null, null, null, compact = true))
        assertEquals("—", formatSourceCell(null, null, null, compact = false))
    }

    // Line = 0 is treated as "unknown" because our codec uses 0 / negative
    // values as sentinels; an actual CSS declaration is always on line 1+.
    @Test
    fun `line zero falls through to fallback`() {
        assertEquals(
            "var(--base)",
            formatSourceCell("variables.css", 0, "var(--base)", compact = true)
        )
    }

    // File present but line missing uses the fallback in both modes.
    @Test
    fun `file without line falls back to first step`() {
        assertEquals(
            "var(--base)",
            formatSourceCell("variables.css", null, "var(--base)", compact = true)
        )
        assertEquals(
            "var(--base)",
            formatSourceCell("variables.css", null, "var(--base)", compact = false)
        )
    }
}
