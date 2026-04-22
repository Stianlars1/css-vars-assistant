package cssvarsassistant.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CssVarQueryMatcherTest {

    @Test
    fun `extracts query from closed var call`() {
        val text = ".card { padding: var(--spacing); }"
        val offset = text.indexOf(")")

        val query = CssVarQueryMatcher.extractQuery(text, offset)

        assertEquals(CssVarQueryMatcher.Query("--spacing", "spacing"), query)
    }

    @Test
    fun `extracts blank query at var function start`() {
        val text = ".card { padding: var(  ); }"
        val offset = text.indexOf(")")

        val query = CssVarQueryMatcher.extractQuery(text, offset)

        assertEquals(CssVarQueryMatcher.Query("", ""), query)
    }

    @Test
    fun `rejects non custom property prefixes`() {
        val text = ".card { padding: var(theme-token); }"
        val offset = text.indexOf(")")

        assertNull(CssVarQueryMatcher.extractQuery(text, offset))
    }

    @Test
    fun `keeps strongest match tier for a query`() {
        val query = CssVarQueryMatcher.Query("--spacing", "spacing")
        val names = listOf(
            "spacing-3xs",
            "spacing-xs",
            "typography-body-letter-spacing",
            "base-spacing-token"
        )

        val strongest = CssVarQueryMatcher.keepStrongestMatches(names, query) { it }

        assertEquals(listOf("spacing-3xs", "spacing-xs"), strongest)
    }

    @Test
    fun `prefers exact foreground prefix over suffix matches`() {
        val query = CssVarQueryMatcher.Query("--fore", "fore")
        val names = listOf(
            "foreground",
            "error-foreground",
            "muted-foreground"
        )

        val strongest = CssVarQueryMatcher.keepStrongestMatches(names, query) { it }

        assertEquals(listOf("foreground"), strongest)
    }

    @Test
    fun `trims trailing query segments until a usable match is found`() {
        val query = CssVarQueryMatcher.Query("--foreground-color", "foreground-color")

        val directMatch = CssVarQueryMatcher.bestMatch("foreground", query)
        val suffixMatch = CssVarQueryMatcher.bestMatch("error-foreground", query)

        assertEquals(CssVarQueryMatcher.MatchKind.PREFIX, directMatch?.kind)
        assertEquals("foreground", directMatch?.matchedPrefix)
        assertEquals(CssVarQueryMatcher.MatchKind.TOKEN_PREFIX, suffixMatch?.kind)
        assertEquals("foreground", suffixMatch?.matchedPrefix)
    }

    // Regression: IntelliJ's completion framework inserts a DUMMY identifier
    // ("IntellijIdeaRulezzz ") at the caret BEFORE running contributors. If
    // the editor caret ends up placed past that dummy and extractQuery reads
    // `params.originalFile.text`, the capture group swallows the dummy and
    // every user-defined variable fails to match against the resulting
    // junk prefix — so the popup falls back to showing every variable
    // sorted by value type. That's exactly what the screenshots show for
    // `hsl(var(--err...))`.
    @Test
    fun `extract query must not capture IntelliJ dummy identifier as part of prefix`() {
        val dummy = "IntellijIdeaRulezzz "
        val text = "color: hsl(var(--err$dummy));"
        // Caret placed past the dummy — the state the contributor sees when
        // it reads params.originalFile.text with caretModel.offset.
        val offset = text.indexOf("));")

        val query = CssVarQueryMatcher.extractQuery(text, offset)

        assertEquals(
            CssVarQueryMatcher.Query("--err", "err"),
            query,
            "extractQuery should strip IntelliJ's completion dummy identifier"
        )
    }

    @Test
    fun `extract query tolerates dummy identifier at top level`() {
        val dummy = "IntellijIdeaRulezzz "
        val text = "color: var(--primar$dummy);"
        val offset = text.indexOf(");")

        val query = CssVarQueryMatcher.extractQuery(text, offset)

        assertEquals(
            CssVarQueryMatcher.Query("--primar", "primar"),
            query
        )
    }

    // ---------------------------------------------------------------------
    // CssVarPrefixMatcher lifecycle (issue #18 follow-up: sticky popup).
    //
    // IntelliJ's lookup calls cloneWithPrefix on every keystroke while the
    // popup is open. The previous anonymous matcher returned `this`, so the
    // prefix froze at whatever the popup opened with. And `prefixMatches`
    // always returned true, so every lookup item passed the filter forever.
    // These tests lock in the fixed behaviour.
    // ---------------------------------------------------------------------
    @Test
    fun `cloneWithPrefix returns a fresh matcher bound to the new prefix`() {
        val initial = CssVarPrefixMatcher("--")
        val next = initial.cloneWithPrefix("--err")

        assertTrue(next is CssVarPrefixMatcher, "cloneWithPrefix must return a CssVarPrefixMatcher")
        assertEquals("--err", next.prefix)
        assertTrue(next !== initial, "cloneWithPrefix must not return the same instance — that's what made the popup sticky")
    }

    @Test
    fun `prefixMatches routes through bestMatch for a non-blank prefix`() {
        val matcher = CssVarPrefixMatcher("--err")

        assertTrue(matcher.prefixMatches("--error"), "--error must match --err as PREFIX")
        assertTrue(matcher.prefixMatches("--error-foreground"), "--error-foreground must match --err as PREFIX")
        assertFalse(matcher.prefixMatches("--ls-normal"), "--ls-normal must NOT pass filter for --err")
        assertFalse(matcher.prefixMatches("--background"), "--background must NOT pass filter for --err")
    }

    @Test
    fun `prefixMatches accepts everything for blank or dash-only prefix`() {
        val blank = CssVarPrefixMatcher("")
        val dashesOnly = CssVarPrefixMatcher("--")

        assertTrue(blank.prefixMatches("--anything"))
        assertTrue(dashesOnly.prefixMatches("--anything"))
        assertTrue(dashesOnly.prefixMatches("--ls-normal"))
    }

    @Test
    fun `prefixMatches narrows progressively as the user types`() {
        var m: PrefixMatcher = CssVarPrefixMatcher("--")
        assertTrue(m.prefixMatches("--ls-normal"))
        assertTrue(m.prefixMatches("--error"))

        m = m.cloneWithPrefix("--e")
        assertFalse(m.prefixMatches("--ls-normal"))
        assertTrue(m.prefixMatches("--error"))
        assertTrue(m.prefixMatches("--error-foreground"))

        m = m.cloneWithPrefix("--err")
        assertFalse(m.prefixMatches("--ls-normal"))
        assertFalse(m.prefixMatches("--background"))
        assertTrue(m.prefixMatches("--error"))
        assertTrue(m.prefixMatches("--error-foreground"))

        m = m.cloneWithPrefix("--error-f")
        // `--error` still passes because our bestMatch has a prefix-backoff:
        // it trims trailing dash-segments until a candidate matches (the same
        // behaviour that lets `--foreground-color` still surface `--foreground`).
        assertTrue(m.prefixMatches("--error"))
        assertTrue(m.prefixMatches("--error-foreground"))
        // Completely unrelated names still drop as the prefix tightens.
        assertFalse(m.prefixMatches("--ls-normal"))
        assertFalse(m.prefixMatches("--background"))
    }

    // Phase 7f: the real IDE hid `--error` for prefix `--er` even though the
    // harness saw both items. Root cause was IntelliJ's lookup arranger
    // running without a matchingDegree signal from our custom PrefixMatcher.
    // These tests lock the new arranger hints in.
    @Test
    fun `matchingDegree ranks exact name above longer variants for same prefix tier`() {
        val matcher = CssVarPrefixMatcher("--er")

        val exact = matcher.matchingDegree("--er")
        val shortPrefix = matcher.matchingDegree("--error")
        val longerPrefix = matcher.matchingDegree("--error-foreground")
        val tokenPrefix = matcher.matchingDegree("--foreground-error")

        assertTrue(
            exact > shortPrefix,
            "exact-name match must outrank longer prefix variants (was exact=$exact vs prefix=$shortPrefix)"
        )
        assertTrue(
            shortPrefix > longerPrefix,
            "shorter prefix match must outrank its longer dash-extended sibling (was $shortPrefix vs $longerPrefix)"
        )
        assertTrue(
            longerPrefix > tokenPrefix,
            "PREFIX tier must outrank TOKEN_PREFIX tier (was $longerPrefix vs $tokenPrefix)"
        )
    }

    @Test
    fun `matchingDegree returns 0 for a non-matching name`() {
        val matcher = CssVarPrefixMatcher("--err")

        assertEquals(0, matcher.matchingDegree("--background"))
    }

    @Test
    fun `isStartMatch is true for prefix and token prefix tiers`() {
        val matcher = CssVarPrefixMatcher("--err")

        assertTrue(matcher.isStartMatch("--error"), "direct prefix must be a start match")
        assertTrue(
            matcher.isStartMatch("--background-error"),
            "token-prefix (dashed-segment start) must count as a start match so the arranger ranks it with prefix matches"
        )
        assertFalse(
            matcher.isStartMatch("--background"),
            "names that don't contain the prefix at any dashed-token boundary must not count as a start match"
        )
    }

    // Phase 7i: `var(-|)` — IntelliJ autopopup fires at the first dash, passing
    // us prefixMatcherPrefix="-". Without special-casing, bestMatch("-") would
    // reject every variable whose display name has no internal dash, which
    // dropped `--error` from the popup and eventually corrupted every
    // subsequent keystroke in the same lookup session (confirmed in idea.log).
    @Test
    fun `withFallback treats single-dash prefix as blank query`() {
        val fallback = CssVarQueryMatcher.withFallback(primary = null, prefixMatcherPrefix = "-")

        assertEquals(
            CssVarQueryMatcher.Query(rawPrefix = "-", normalizedPrefix = ""),
            fallback,
            "single-dash prefix must yield a blank-normalized query so bestMatch admits every variable"
        )
    }

    @Test
    fun `withFallback treats triple-dash prefix as blank query`() {
        val fallback = CssVarQueryMatcher.withFallback(primary = null, prefixMatcherPrefix = "---")

        assertEquals(
            CssVarQueryMatcher.Query(rawPrefix = "---", normalizedPrefix = ""),
            fallback
        )
    }

    @Test
    fun `CssVarPrefixMatcher with single-dash prefix admits every variable`() {
        val matcher = CssVarPrefixMatcher("-")

        assertTrue(matcher.prefixMatches("--error"), "single-dash matcher must admit `--error`")
        assertTrue(matcher.prefixMatches("--foreground"))
        assertTrue(matcher.prefixMatches("--background"))
        assertTrue(matcher.prefixMatches("--ls-normal"))
    }

    @Test
    fun `extract query returns blank query for bare var open paren`() {
        val dummy = "IntellijIdeaRulezzz "
        // Caret right after `var(`, so the dummy is the only content of the arg.
        val text = "color: var($dummy);"
        val offset = text.indexOf(");")

        val query = CssVarQueryMatcher.extractQuery(text, offset)

        // Dummy alone is not a custom property start, so we should either
        // return a blank Query or null. In practice, the completion code
        // treats a null/blank result as "show everything", which is correct
        // for `var(|)`.
        assertEquals(
            CssVarQueryMatcher.Query("", ""),
            query
        )
    }
}
