package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals

class HoverRowSortTest {

    // Minimal shape the comparator reads so the test doesn't have to construct
    // a full `EntryWithSource` with a live `ResolutionInfo` just to verify sort
    // behaviour.
    private data class Row(
        val context: String,
        val sourceFile: String?,
        val sourceLine: Int?
    )

    private fun sort(rows: List<Row>): List<Row> = rows.sortedWith(
        hoverRowComparator(
            context = { it.context },
            sourceFile = { it.sourceFile },
            sourceLine = { it.sourceLine }
        )
    )

    // Phase 8a follow-up: the live smoke test for issue #19 surfaced that
    // `.theme-hc` was sorting ahead of `[data-theme="dark"]` because
    // `RankUtil.rank()` falls back to alphabetical context within its
    // catch-all bucket 9, and `.` < `[` in ASCII. This test locks in that
    // source order (top-to-bottom in the file) wins inside a bucket.
    @Test
    fun `selectors in the same file sort by line number not alphabet`() {
        val rows = listOf(
            Row(".theme-hc", "variables.css", 225),
            Row("[data-theme=\"dark\"]", "variables.css", 222)
        )

        assertEquals(
            listOf("[data-theme=\"dark\"]", ".theme-hc"),
            sort(rows).map { it.context }
        )
    }

    // Mirror of the live smoke-test case: default/light + dark-mode media query
    // + two themed selectors should render as
    //   Light mode, Dark mode, [data-theme="dark"], .theme-hc
    // The first two come from the bucket hierarchy (0 = default, 1 = dark
    // mode); the last two come from the new (file, line) tiebreaker inside
    // bucket 9.
    @Test
    fun `full popup order matches bucket hierarchy then source order`() {
        val rows = listOf(
            Row(".theme-hc", "variables.css", 225),
            Row("[data-theme=\"dark\"]", "variables.css", 222),
            Row("default", "variables.css", 219),
            Row("(prefers-color-scheme: dark)", "variables.css", 230)
        )

        assertEquals(
            listOf(
                "default",
                "(prefers-color-scheme: dark)",
                "[data-theme=\"dark\"]",
                ".theme-hc"
            ),
            sort(rows).map { it.context }
        )
    }

    // Entries with a known line come before entries where the index didn't
    // record one (legacy 3-part records or computed-only rows). This prevents
    // unknown-source rows from jumping to the top inside a bucket.
    @Test
    fun `unknown source line sorts after known lines in the same bucket`() {
        val rows = listOf(
            Row("[data-theme=\"legacy\"]", "tokens.css", null),
            Row("[data-theme=\"dark\"]", "tokens.css", 10)
        )

        assertEquals(
            listOf("[data-theme=\"dark\"]", "[data-theme=\"legacy\"]"),
            sort(rows).map { it.context }
        )
    }

    // Multiple files: groups by file (alphabetical) then by line within each
    // file. Selectors spread across packages (shadcn/radix/tailwind theme
    // files all redefining the same token) stay grouped instead of being
    // interleaved by line alone.
    @Test
    fun `sorts by source file first then source line within each file`() {
        val rows = listOf(
            Row("[data-theme=\"dark\"]", "zeta.css", 5),
            Row(".dark", "alpha.css", 20),
            Row(".high-contrast", "alpha.css", 5)
        )

        assertEquals(
            listOf(".high-contrast", ".dark", "[data-theme=\"dark\"]"),
            sort(rows).map { it.context }
        )
    }
}
