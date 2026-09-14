package cssvarsassistant.completion

import cssvarsassistant.settings.CssVarsAssistantSettings.SortingOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CssVariableComparatorContractTest {
    private val completion = CssVariableCompletion()

    private fun entry(name: String, value: String, query: String = "pa") = CssVariableCompletion.Entry(
        rawName = "--$name",
        display = name,
        mainValue = value,
        allValues = listOf("default" to value),
        doc = "",
        isAllColor = false,
        derived = false,
        matchedQuery = query
    )

    @Test
    fun `mixed sizes colors and numbers have a transitive ordering`() {
        val entries = listOf(
            entry("padding-really-super-x", "4px"),
            entry("pa-color-x", "#000000"),
            entry("pa-nu", "0.5")
        )
        for (order in SortingOrder.entries) {
            assertComparatorContract(entries, completion.createSmartComparator(order, "pa"))
        }
    }

    @Test
    fun `sorting a large mixed token catalog does not throw`() {
        val entries = (0 until 40).flatMap { i ->
            listOf(
                entry("padding-really-super-x-$i", "4px"),
                entry("pa-color-x-$i", "#000000"),
                entry("pa-nu-$i", "0.5")
            )
        }
        for (order in listOf(SortingOrder.ASC, SortingOrder.DESC)) {
            val comparator = completion.createSmartComparator(order, "pa")
            val batches = (0 until 64).map { seed ->
                entries.shuffled(Random(seed)).sortedWith(comparator)
            }
            batches.forEachIndexed { seed, sorted ->
                assertEquals(entries.toSet(), sorted.toSet(), "$order seed=$seed")
                for (i in sorted.indices) for (j in i + 1 until sorted.size) {
                    assertTrue(
                        comparator.compare(sorted[i], sorted[j]) <= 0,
                        "$order seed=$seed: ${sorted[i].display} must not follow ${sorted[j].display}"
                    )
                }
            }
        }
    }

    @Test
    fun `mixed catalog obeys the comparator contract across matching tiers and queries`() {
        val entries = listOf(
            entry("pa", "inherit"),
            entry("pa-1", "12px"),
            entry("pa-2", "#ffffff"),
            entry("pa-10", "0.5"),
            entry("pa-2147483647", "none"),
            entry("padding-really-super-x", "4px"),
            entry("pa-color-x", "#000000"),
            entry("PA-COLOR-X", "#000000"),
            entry("pa-nu", "0.5"),
            entry("pa-long-size", "4px"),
            entry("pa-long-color", "#000000"),
            entry("pa-long-number", "0.5"),
            entry("pa-long-keyword", "none"),
            entry("pa-other", "auto"),
            entry("theme-pa", "1rem"),
            entry("theme-pa-long", "#ffffff"),
            entry("theme-padding", "-1"),
            entry("theme-padded-long", "inherit"),
            entry("opaque", "8px"),
            entry("opaque-color", "#ff0000"),
            entry("opaque-number", "0.5"),
            entry("opaque-keyword", "auto")
        )
        for (query in listOf("pa", "pa-long", "pa-1", "PA", "")) {
            val matches = entries.mapNotNull { entry ->
                CssVarQueryMatcher.bestMatch(entry.display, CssVarQueryMatcher.Query("--$query", query))
                    ?.let { match -> entry.copy(matchPriority = match.kind.priority, matchedQuery = match.matchedPrefix) }
            }
            for (order in SortingOrder.entries) {
                assertComparatorContract(matches, completion.createSmartComparator(order, query))
            }
        }
    }

    @Test
    fun `numeric families retain natural order including the largest integer suffix`() {
        val entries = listOf(
            entry("pa-label", "#000000"),
            entry("pa-2147483647", "4px"),
            entry("pa-10", "0.5"),
            entry("pa-2", "#ffffff"),
            entry("pa-1", "12px")
        )
        for (order in listOf(SortingOrder.ASC, SortingOrder.DESC)) {
            assertEquals(listOf("pa-1", "pa-2", "pa-10", "pa-2147483647", "pa-label"), names(entries, order, "pa"))
        }
    }

    @Test
    fun `semantic size families retain ascending and descending value order`() {
        val entries = listOf(
            entry("spacing-m", "16px", "spacing"),
            entry("spacing-s", "12px", "spacing"),
            entry("spacing-xs", "8px", "spacing"),
            entry("spacing-2xs", "4px", "spacing")
        )
        val ascending = listOf("spacing-2xs", "spacing-xs", "spacing-s", "spacing-m")
        assertEquals(ascending, names(entries, SortingOrder.ASC, "spacing"))
        assertEquals(ascending.reversed(), names(entries, SortingOrder.DESC, "spacing"))
    }

    @Test
    fun `unitless numbers retain value order regardless of name length`() {
        val entries = listOf(
            entry("layer-base", "0", "layer"),
            entry("layer-dropdown", "10", "layer"),
            entry("layer-top", "100", "layer")
        )
        val ascending = listOf("layer-base", "layer-dropdown", "layer-top")
        assertEquals(ascending, names(entries, SortingOrder.ASC, "layer"))
        assertEquals(ascending.reversed(), names(entries, SortingOrder.DESC, "layer"))
    }

    @Test
    fun `exact names still outrank numeric values`() {
        val entries = listOf(entry("pa-size", "4px"), entry("pa", "#000000"), entry("pa-number", "0.5"))
        for (order in listOf(SortingOrder.ASC, SortingOrder.DESC)) {
            assertEquals("pa", names(entries, order, "pa").first())
        }
    }

    @Test
    fun `full query prefixes outrank truncated matches for every value type`() {
        val values = listOf("4px", "#000000", "0.5", "auto")
        for (fullValue in values) for (truncatedValue in values) {
            val entries = listOf(
                entry("pa", truncatedValue, "pa"),
                entry("pa-long", fullValue, "pa-lo")
            )
            for (order in listOf(SortingOrder.ASC, SortingOrder.DESC)) {
                assertEquals(listOf("pa-long", "pa"), names(entries, order, "pa-lo"), "$order $fullValue / $truncatedValue")
            }
        }
    }

    @Test
    fun `blank queries and alphabetical preference keep name ordering`() {
        val entries = listOf(entry("padding", "4px"), entry("pa-color", "#000000"), entry("pa-number", "0.5"))
        val alphabetical = listOf("pa-color", "pa-number", "padding")
        for (order in SortingOrder.entries) assertEquals(alphabetical, names(entries, order, ""))
        assertEquals(alphabetical, names(entries, SortingOrder.ALPHABETICAL, "pa"))
    }

    private fun names(entries: List<CssVariableCompletion.Entry>, order: SortingOrder, query: String) =
        entries.sortedWith(completion.createSmartComparator(order, query)).map { it.display }

    private fun assertComparatorContract(
        entries: List<CssVariableCompletion.Entry>,
        comparator: Comparator<CssVariableCompletion.Entry>
    ) {
        fun sign(value: Int) = value.compareTo(0)
        for (a in entries) {
            assertEquals(0, comparator.compare(a, a), "Reflexivity: ${a.display}")
            for (b in entries) {
                val ab = sign(comparator.compare(a, b))
                assertEquals(-ab, sign(comparator.compare(b, a)), "Antisymmetry: ${a.display}, ${b.display}")
                for (c in entries) {
                    val bc = sign(comparator.compare(b, c))
                    val ac = sign(comparator.compare(a, c))
                    val context = "${a.display}, ${b.display}, ${c.display}"
                    if (ab < 0 && bc < 0) assertTrue(ac < 0, "Transitivity: $context")
                    if (ab == 0) assertEquals(ac, bc, "Equivalent entries: $context")
                }
            }
        }
    }
}
