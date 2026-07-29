package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #29 — after the parser splits root+theme selector lists into a
 * `default` entry and a theme entry with the same value, collapse-by-value
 * merges them into a single row. This test locks in the desired merged
 * label formatting:
 *
 * - Only `Default` → `Default`
 * - `Default` + one theme  → `Default/<Theme>`
 * - `Default` + several themes → `Default/<Theme1>, <Theme2>, …`
 * - No `Default` in the set → existing behaviour (join with ", ")
 */
class DefaultThemeLabelMergeTest {

    private data class Row(
        val context: String,
        val value: String,
        val combinesWithDefault: Boolean = false
    )

    private fun collapse(rows: List<Row>, maxLabelLength: Int = 80): List<Row> =
        collapseRowsByValue(
            rows = rows,
            value = { it.value },
            label = { it.context },
            merge = { first, mergedLabel -> first.copy(context = mergedLabel) },
            combineWithDefault = { it.combinesWithDefault },
            maxLabelLength = maxLabelLength
        )

    // Case 1 / Case 2 from #29 — one theme merged with Default.
    @Test
    fun `default plus single theme joins with slash`() {
        val rows = listOf(
            Row("Default", "blue"),
            Row("Light", "blue", combinesWithDefault = true),
            Row("Dark", "green")
        )

        assertEquals(
            listOf(
                Row("Default/Light", "blue"),
                Row("Dark", "green")
            ),
            collapse(rows)
        )
    }

    // Case 3 — plain root + selector-list root+theme both produce Default
    // and Light rows. Duplicate "Default" labels should be deduplicated.
    @Test
    fun `duplicate default labels dedupe cleanly under the slash join`() {
        val rows = listOf(
            Row("Default", "blue"),
            Row("Default", "blue"),
            Row("Light", "blue", combinesWithDefault = true),
            Row("Dark", "green")
        )

        assertEquals(
            listOf(
                Row("Default/Light", "blue"),
                Row("Dark", "green")
            ),
            collapse(rows)
        )
    }

    // Case 4 baseline — Default alone stays "Default".
    @Test
    fun `default alone stays default`() {
        val rows = listOf(
            Row("Default", "blue"),
            Row("Dark", "green")
        )

        assertEquals(rows, collapse(rows))
    }

    // Real-world Nova-Light example from #29's spec: the theme name is
    // derived from the actual selector, not hardcoded to "Light".
    @Test
    fun `default combined with an arbitrary theme keeps its name`() {
        val rows = listOf(
            Row("Default", "#fafafa"),
            Row("Nova Light", "#fafafa", combinesWithDefault = true)
        )

        assertEquals(
            listOf(Row("Default/Nova Light", "#fafafa")),
            collapse(rows)
        )
    }

    // Multiple themes merged with Default — join all after the slash with
    // commas, but keep the slash between Default and the theme list.
    @Test
    fun `default plus multiple themes joins with slash then commas`() {
        val rows = listOf(
            Row("Default", "#fff"),
            Row("Light", "#fff", combinesWithDefault = true),
            Row("Nova Light", "#fff", combinesWithDefault = true),
            Row("Sepia", "#fff", combinesWithDefault = true)
        )

        assertEquals(
            listOf(Row("Default/Light, Nova Light, Sepia", "#fff")),
            collapse(rows)
        )
    }

    // Non-Default merges stay on the existing "," join — nothing regresses
    // for design systems with many equal-value themes and no Default row.
    @Test
    fun `theme-only merges keep the existing comma join`() {
        val rows = listOf(
            Row("Light mode", "#fff"),
            Row("Catppuccin", "#fff"),
            Row("Sepia", "#fff")
        )

        assertEquals(
            listOf(Row("Light mode, Catppuccin, Sepia", "#fff")),
            collapse(rows)
        )
    }

    @Test
    fun `default does not slash-join unrelated equal-value contexts`() {
        val rows = listOf(
            Row("Default", "#fff"),
            Row("Print", "#fff"),
            Row("Reduced motion", "#fff")
        )

        assertEquals(
            listOf(Row("Default, Print, Reduced motion", "#fff")),
            collapse(rows)
        )
    }
}
