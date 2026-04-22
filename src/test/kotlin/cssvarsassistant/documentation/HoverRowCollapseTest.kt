package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals

class HoverRowCollapseTest {

    // Minimal shape the collapse helper reads. Tests use String labels
    // already pre-prettified, since collapse sits after context-label
    // rendering in the real pipeline.
    private data class Row(val context: String, val value: String)

    private fun collapse(rows: List<Row>, maxLabelLength: Int = 80): List<Row> =
        collapseRowsByValue(
            rows = rows,
            value = { it.value },
            label = { it.context },
            merge = { first, mergedLabel -> first.copy(context = mergedLabel) },
            maxLabelLength = maxLabelLength
        )

    // 1.8.3 — the headline case from LordMaddhi's feedback: many themes
    // share the same token value, merging keeps the popup scannable.
    @Test
    fun `merges rows with identical values into one row with joined labels`() {
        val rows = listOf(
            Row("Light mode", "#ffffff"),
            Row("Catppuccin", "#ffffff"),
            Row("Sepia", "#ffffff"),
            Row("Dark mode", "#000000")
        )

        assertEquals(
            listOf(
                Row("Light mode, Catppuccin, Sepia", "#ffffff"),
                Row("Dark mode", "#000000")
            ),
            collapse(rows)
        )
    }

    // Merged row's sort position = first occurrence position. Contributing
    // rows later in the input get absorbed into their earlier-seen sibling.
    @Test
    fun `first occurrence of each value keeps its sort position`() {
        val rows = listOf(
            Row("A", "v1"),
            Row("B", "v2"),
            Row("C", "v1"),
            Row("D", "v3"),
            Row("E", "v2")
        )

        assertEquals(
            listOf(
                Row("A, C", "v1"),
                Row("B, E", "v2"),
                Row("D", "v3")
            ),
            collapse(rows)
        )
    }

    // Duplicate labels for the same value should NOT double up. Can happen
    // when a variable is declared twice with the same context + value across
    // different files (e.g. two imports contributing the same token).
    @Test
    fun `deduplicates identical labels within a merged row`() {
        val rows = listOf(
            Row("Light mode", "#ffffff"),
            Row("Light mode", "#ffffff"),
            Row("Catppuccin", "#ffffff")
        )

        assertEquals(
            listOf(Row("Light mode, Catppuccin", "#ffffff")),
            collapse(rows)
        )
    }

    // Singleton values pass through untouched — merging only applies when
    // there are actually multiple rows with the same value.
    @Test
    fun `singleton values pass through unchanged`() {
        val rows = listOf(
            Row("Light mode", "#ffffff"),
            Row("Dark mode", "#000000"),
            Row("Sepia", "#f1e5d1")
        )

        assertEquals(rows, collapse(rows))
    }

    // Empty input → empty output.
    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList(), collapse(emptyList()))
    }

    // Very long merged labels get truncated with an ellipsis so big theme
    // systems (12+ variants sharing a value) don't blow out the Context
    // column width.
    @Test
    fun `truncates merged labels longer than the max length`() {
        val labels = List(20) { "Theme-$it" } // 20 × ~8 chars = ~150 chars joined
        val rows = labels.map { Row(it, "v1") }

        val merged = collapse(rows, maxLabelLength = 40).single()

        assert(merged.context.endsWith("…")) {
            "expected truncated label to end with ellipsis, got: ${merged.context}"
        }
        assert(merged.context.length <= 40) {
            "expected truncated label <= 40 chars, got ${merged.context.length}"
        }
    }

    // If all merged rows have the same context string (weird but possible
    // after custom distinctBy upstream), the dedup collapses them to one.
    @Test
    fun `single-label merge renders the label once`() {
        val rows = listOf(
            Row("Default", "white"),
            Row("Default", "white")
        )

        assertEquals(
            listOf(Row("Default", "white")),
            collapse(rows)
        )
    }
}
