package cssvarsassistant.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
