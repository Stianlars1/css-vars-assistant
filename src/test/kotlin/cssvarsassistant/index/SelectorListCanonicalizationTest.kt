package cssvarsassistant.index

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #29 — context normalization for root + theme selector variants.
 *
 * Historic behaviour: any selector-list that did not equal a single root-like
 * selector was persisted verbatim (`:root, [data-theme=light]`,
 * `:root, [data-theme="light"]`, `.dark, [data-theme="dark"]`, …), and
 * downstream that produced raw selector-list labels, duplicate merged labels
 * when only the quoting differed, and mixed "Light mode, :root, [data-theme=…]"
 * cells in the hover popup.
 *
 * v1.9.2 canonicalises the list at parse time so the index — and every
 * downstream label collapse — sees a stable, semantically-meaningful context
 * per declaration.
 */
class SelectorListCanonicalizationTest {

    // Case 1 from #29: `:root, [data-theme="light"]` + explicit dark theme.
    // The declaration semantically applies to BOTH the document root AND the
    // explicit light theme, so we emit two entries — one `default`, one for
    // the canonicalised theme selector — with the same value. Collapse-by-value
    // downstream then merges them into a single `Default/Light` row.
    @Test
    fun `root plus theme selector list emits both default and theme entries`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root, [data-theme="light"] {
              --foo: blue;
            }
            [data-theme='dark'] {
              --foo: green;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--foo", "default", "blue", "", line = 2),
                ParsedCssVariableEntry("--foo", "[data-theme=light]", "blue", "", line = 2),
                ParsedCssVariableEntry("--foo", "[data-theme=dark]", "green", "", line = 5)
            ),
            entries
        )
    }

    // Case 2: two selector-list variants differing only in quoting collapse
    // to the same canonical shape and therefore land as identical index
    // entries (the downstream `distinctBy { context to value }` step then
    // dedupes them). Both selector lists include `:root`, so a `default`
    // entry is also emitted from each.
    @Test
    fun `quoted and unquoted attribute equals canonicalise to the same context`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root, [data-theme=light] {
              --foo: blue;
            }
            :root, [data-theme="light"] {
              --foo: blue;
            }
            """.trimIndent()
        )

        // Both selector-lists produce the same pair of entries; the parser
        // still emits each declaration once (dedup is downstream). What
        // matters here is that the two theme-context strings are identical.
        val themeContexts = entries.filter { it.context != "default" }.map { it.context }.distinct()
        assertEquals(listOf("[data-theme=light]"), themeContexts)
    }

    // Case 3: comma list that includes `:root` alongside a plain `:root`
    // declaration. The list becomes (default + theme), the plain `:root`
    // becomes default. Downstream collapse-by-value ensures the popup shows
    // one row per unique value.
    @Test
    fun `root plus theme selector list plus plain root produces stable context set`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root, [data-theme=light] {
              --foo: blue;
            }
            :root {
              --foo: blue;
            }
            [data-theme='dark'] {
              --foo: green;
            }
            """.trimIndent()
        )

        val contexts = entries.map { it.context }.distinct()
        // Exactly three distinct contexts: default (from both root sources),
        // canonical light theme, canonical dark theme.
        assertEquals(
            listOf("default", "[data-theme=light]", "[data-theme=dark]"),
            contexts
        )
    }

    // Case 4 baseline: plain `:root {}` + explicit dark theme. This one already
    // worked correctly; we lock it in so the fix doesn't regress it.
    @Test
    fun `plain root plus dark theme is unchanged`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --foo: blue;
            }
            [data-theme='dark'] {
              --foo: green;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--foo", "default", "blue", "", line = 2),
                ParsedCssVariableEntry("--foo", "[data-theme=dark]", "green", "", line = 5)
            ),
            entries
        )
    }

    // Case 5: plain root + separate explicit light theme + dark theme.
    // The parser records `default`, canonical `[data-theme=light]`, and
    // canonical `[data-theme=dark]` — collapse-by-value downstream merges
    // the light + default rows into a single `Default/Light` label.
    @Test
    fun `plain root plus explicit light theme keeps separate but canonical contexts`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --foo: blue;
            }
            [data-theme="light"] {
              --foo: blue;
            }
            [data-theme='dark'] {
              --foo: green;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--foo", "default", "blue", "", line = 2),
                ParsedCssVariableEntry("--foo", "[data-theme=light]", "blue", "", line = 5),
                ParsedCssVariableEntry("--foo", "[data-theme=dark]", "green", "", line = 8)
            ),
            entries
        )
    }

    // A selector list where every element is root-like still collapses to
    // `default` (the caller doesn't have to emit anything specific per root
    // variant). `:root, :host, html` all mean "document root" in a token
    // context, matching the existing single-selector root-like handling.
    @Test
    fun `all-root-like selector list collapses to default`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root, html, body {
              --foo: white;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--foo", "default", "white", "", line = 2)),
            entries
        )
    }

    // A selector list with no root-like elements is preserved as a
    // canonicalised, deduplicated single string. Quoting is normalised so
    // downstream doesn't see two shapes of the same list.
    @Test
    fun `theme-only selector list is canonicalised and deduplicated`() {
        val entries = CssVariableEntryParser.parse(
            """
            [data-theme='dark'], [data-theme="dark"] {
              --foo: black;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--foo", "[data-theme=dark]", "black", "", line = 2)
            ),
            entries
        )
    }

    // Class selectors + attribute selectors coexisting stay verbatim (both
    // are theme selectors, neither is root-like). Quoting is normalised.
    @Test
    fun `mixed class and attribute theme selector list is canonicalised`() {
        val entries = CssVariableEntryParser.parse(
            """
            .dark, [data-theme="dark"] {
              --bg: black;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--bg", ".dark, [data-theme=dark]", "black", "", line = 2)
            ),
            entries
        )
    }
}
