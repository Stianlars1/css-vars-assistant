package cssvarsassistant.completion

import cssvarsassistant.settings.CssVarsAssistantSettings
import kotlin.test.Test
import kotlin.test.assertEquals

// 1.8.4 — pure unit tests for the contributor's sort logic (no IDE fixture).
// Complements the matcher-level tests in CssVarQueryMatcherTest and the
// platform-harness coverage in CssVariableCompletionHarnessTest.
class CssVariableCompletionSortTest {

    private fun entry(display: String, matchedPrefix: String, value: String = "#000000"): CssVariableCompletion.Entry =
        CssVariableCompletion.Entry(
            rawName = "--$display",
            display = display,
            mainValue = value,
            allValues = listOf("default" to value),
            doc = "",
            isAllColor = false,
            derived = false,
            matchPriority = CssVarQueryMatcher.MatchKind.PREFIX.priority,
            matchedQuery = matchedPrefix
        )

    private fun sort(query: String, items: List<CssVariableCompletion.Entry>): List<String> {
        val comparator = CssVariableCompletion().createSmartComparator(
            CssVarsAssistantSettings.SortingOrder.ASC,
            query
        )
        return items.sortedWith(comparator).map { it.display }
    }

    // Issue #20 headline case: typing the full variable name `sidebar-accent-foreground`
    // must put that exact item at the top of the popup — not `sidebar`, which only
    // matched because the matcher fell back to a truncated candidate `sidebar`.
    // Pressing Enter on the default-selected first item would otherwise overwrite
    // what the user already typed, which is the Blinks44 bug report.
    @Test
    fun `exact full-name match ranks first when user typed the whole variable name`() {
        val items = listOf(
            entry("sidebar", matchedPrefix = "sidebar"),
            entry("sidebar-accent", matchedPrefix = "sidebar-accent"),
            entry("sidebar-accent-foreground", matchedPrefix = "sidebar-accent-foreground")
        )

        val sorted = sort(query = "sidebar-accent-foreground", items = items)

        assertEquals(
            listOf("sidebar-accent-foreground", "sidebar-accent", "sidebar"),
            sorted
        )
    }

    // Regression coverage for 1.8.0 issue #18 behaviour: the short prefix `er`
    // must still rank `--error` above `--error-foreground`. The new
    // "starts with full query" check kicks in when BOTH items start with the
    // query, so the length tiebreaker fires and preserves the older ordering.
    @Test
    fun `shorter name wins when both items start with the user query`() {
        val items = listOf(
            entry("error-foreground", matchedPrefix = "er"),
            entry("error", matchedPrefix = "er")
        )

        val sorted = sort(query = "er", items = items)

        assertEquals(listOf("error", "error-foreground"), sorted)
    }

    // Mid-length full-prefix match beats a truncated-prefix match even when
    // the truncated-prefix item has a shorter name. User types `sidebar-acc` —
    // both `sidebar-accent` and `sidebar-accent-foreground` start with the
    // full query; `sidebar` does NOT (only matched via truncation).
    @Test
    fun `items starting with the full query outrank truncated-prefix matches`() {
        val items = listOf(
            entry("sidebar", matchedPrefix = "sidebar"),
            entry("sidebar-accent", matchedPrefix = "sidebar-acc"),
            entry("sidebar-accent-foreground", matchedPrefix = "sidebar-acc")
        )

        val sorted = sort(query = "sidebar-acc", items = items)

        // `sidebar-accent` first (shortest that starts with full query),
        // then `sidebar-accent-foreground` (also starts with full query),
        // then `sidebar` (truncated-match fallback).
        assertEquals(
            listOf("sidebar-accent", "sidebar-accent-foreground", "sidebar"),
            sorted
        )
    }

    // Items that share an identical mainValue type-bucket (non-numeric here)
    // and neither starts with the full user query fall through to the
    // matchedPrefix-length tiebreaker. More-consumed-query beats less.
    @Test
    fun `more-consumed query length wins for token-only matches`() {
        val items = listOf(
            // Both are token-prefix matches; neither display starts with
            // the full "dark-bg" query. matchedQuery reflects how much of
            // the query each item actually consumed via candidatePrefixes.
            entry("theme-dark-background", matchedPrefix = "dark-b", value = "#000000"),
            entry("theme-dark", matchedPrefix = "dark", value = "#111111")
        )

        val sorted = sort(query = "dark-b", items = items)

        // "theme-dark-background" consumed more of the user's query (6 chars)
        // than "theme-dark" (4 chars), so it ranks first despite being longer.
        assertEquals(
            listOf("theme-dark-background", "theme-dark"),
            sorted
        )
    }
}
