package cssvarsassistant.index

import kotlin.test.Test
import kotlin.test.assertEquals

class CssVariableEntryParserTest {

    @Test
    fun `keeps media context active after nested rule closes`() {
        val entries = CssVariableEntryParser.parse(
            """
            @media (min-width: 768px) {
              .card {
                color: red;
              }

              :root {
                --layout-gap: 24px;
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--layout-gap", "(min-width: 768px)", "24px", "", line = 7)),
            entries
        )
    }

    @Test
    fun `normalizes compound media query labels`() {
        val entries = CssVariableEntryParser.parse(
            """
            @media screen and (min-width: 768px) and (prefers-color-scheme: dark) {
              :root {
                --surface-accent: #111111;
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry(
                    "--surface-accent",
                    "screen and (min-width: 768px) and (prefers-color-scheme: dark)",
                    "#111111",
                    "",
                    line = 3
                )
            ),
            entries
        )
    }

    @Test
    fun `keeps media context when opening brace is on the next line`() {
        val entries = CssVariableEntryParser.parse(
            """
            @media screen and (min-width: 768px)
            {
              :root {
                --content-width: 72rem;
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry(
                    "--content-width",
                    "screen and (min-width: 768px)",
                    "72rem",
                    "",
                    line = 4
                )
            ),
            entries
        )
    }

    @Test
    fun `parses multiline custom property values`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --hero-shadow:
                0 8px 24px rgba(0, 0, 0, 0.12),
                0 2px 6px rgba(0, 0, 0, 0.08);
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry(
                    "--hero-shadow",
                    "default",
                    "0 8px 24px rgba(0, 0, 0, 0.12), 0 2px 6px rgba(0, 0, 0, 0.08)",
                    "",
                    line = 2
                )
            ),
            entries
        )
    }

    // Regression: a single line that starts with /* ... */ and then has a real
    // variable declaration after the comment must still index the variable.
    // Historic bug: the parser fell into `continue` after consuming the inline
    // comment and never processed the rest of the line.
    @Test
    fun `inline leading block comment does not drop trailing declaration`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              /* blue */ --primary: #0000ff;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--primary", "default", "#0000ff", "blue", line = 2)),
            entries
        )
    }

    // Regression: minified CSS files often have everything on one line and
    // begin with a copyright/license comment. The old CssVariableIndex.kt
    // loop used to `continue` after consuming the single-line comment, so
    // the `:root{...}` block on the rest of the same line was never indexed.
    // Community contributor @pierreoa reported and independently fixed this
    // in the pre-refactor code path (PR #17). The parser-extraction refactor
    // in commit b07ff26 moved the logic out of CssVariableIndex.kt and into
    // CssVariableEntryParser.kt; this test locks in that the same minified
    // case works through the new code path.
    @Test
    fun `minified css starting with a comment still indexes the declaration after it`() {
        val entries = CssVariableEntryParser.parse(
            "/* Copyright 2026 Example Corp */:root{--primary:#ff0000;--size:4px}"
        )

        // The leading comment is associated with the FIRST declaration only
        // (matching how multi-declaration lines have always been handled):
        // `index == 0` gets lastComment, subsequent declarations on the same
        // line get an empty comment. Pierreoa's original bug was that the
        // SECOND declaration was never emitted at all — this test locks in
        // that both now appear, with the comment on the first only.
        assertEquals(
            listOf(
                ParsedCssVariableEntry("--primary", "default", "#ff0000", "Copyright 2026 Example Corp", line = 1),
                ParsedCssVariableEntry("--size", "default", "4px", "", line = 1)
            ),
            entries
        )
    }

    // Regression: an inline /* ... */ segment inside a value must not leak
    // into the stored value text. Without the fix the indexed value was
    // `/* inline */ 1` which then showed up in completion/documentation.
    @Test
    fun `inline comment inside value is stripped`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --size: /* legacy */ 1px;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--size", "default", "1px", "", line = 2)),
            entries
        )
    }

    // Regression: a trailing /* ... */ after the declaration on the same line
    // must also be stripped from the value.
    @Test
    fun `trailing inline comment in value is stripped`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --size: 1px /* px */;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--size", "default", "1px", "", line = 2)),
            entries
        )
    }

    // Phase 8a — issue #19. Attribute selectors like [data-theme="dark"] are a
    // common theming pattern (shadcn, Radix, Tailwind's data-theme mode). Before
    // 1.8.1 every non-root block collapsed into "default" and the last
    // declaration silently won, hiding the dark-mode value from the popup.
    @Test
    fun `attribute selector block is tagged as its own context`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --bg: white;
            }
            [data-theme="dark"] {
              --bg: black;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--bg", "default", "white", "", line = 2),
                ParsedCssVariableEntry("--bg", "[data-theme=\"dark\"]", "black", "", line = 5)
            ),
            entries
        )
    }

    // Phase 8a — class selector. Common in Tailwind v4 `.dark` mode and many
    // hand-rolled theme systems.
    @Test
    fun `class selector block is tagged as its own context`() {
        val entries = CssVariableEntryParser.parse(
            """
            .dark {
              --bg: black;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(ParsedCssVariableEntry("--bg", ".dark", "black", "", line = 2)),
            entries
        )
    }

    // Phase 8a — selectors that are functionally the document root keep the
    // existing "default" context. :root, :host (web components), html, body,
    // and universal * are all documented ways to declare design tokens at the
    // top of the cascade.
    @Test
    fun `root-like selectors are treated as default context`() {
        val rootSelectors = listOf(":root", ":host", "html", "body", "*")
        rootSelectors.forEach { selector ->
            val entries = CssVariableEntryParser.parse(
                """
                $selector {
                  --bg: white;
                }
                """.trimIndent()
            )

            assertEquals(
                listOf(ParsedCssVariableEntry("--bg", "default", "white", "", line = 2)),
                entries,
                "selector `$selector` should resolve to default context"
            )
        }
    }

    // Phase 8a — nesting a themed selector inside a media query combines both
    // labels so the popup can show "(prefers-color-scheme: dark) .hc" and the
    // user can tell which declaration wins under which environment.
    @Test
    fun `non-root selector nested inside media combines labels`() {
        val entries = CssVariableEntryParser.parse(
            """
            @media (prefers-color-scheme: dark) {
              .hc {
                --bg: #000;
              }
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry(
                    "--bg",
                    "(prefers-color-scheme: dark) .hc",
                    "#000",
                    "",
                    line = 3
                )
            ),
            entries
        )
    }

    // Phase 8a — comma-separated selector lists are preserved verbatim so the
    // user can see that "both .dark and [data-theme=dark] got this value" in
    // one row rather than two duplicated rows.
    @Test
    fun `comma-separated selector list stays verbatim`() {
        val entries = CssVariableEntryParser.parse(
            """
            .dark, [data-theme="dark"] {
              --bg: black;
            }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry(
                    "--bg",
                    ".dark, [data-theme=\"dark\"]",
                    "black",
                    "",
                    line = 2
                )
            ),
            entries
        )
    }

    // Phase 8a — pathological giant selector lists (120+ chars) should be
    // truncated with an ellipsis so the Context column doesn't blow up the
    // popup width. Target limit is ~60 chars.
    @Test
    fun `long selector list is truncated with ellipsis`() {
        val longSelector = List(12) { ".class-$it" }.joinToString(", ")
        val entries = CssVariableEntryParser.parse(
            """
            $longSelector {
              --bg: black;
            }
            """.trimIndent()
        )

        assertEquals(1, entries.size)
        val context = entries.single().context
        assert(context.endsWith("…")) { "expected truncated selector to end with ellipsis, was: $context" }
        assert(context.length <= 61) { "expected truncated selector <= 61 chars, was ${context.length}: $context" }
    }

    // Phase 8a — single-line block with multiple declarations (a very common
    // pattern in minified/compressed stylesheets) must attribute EVERY
    // declaration inside the block to the same selector context.
    @Test
    fun `single-line selector block tags all declarations with selector`() {
        val entries = CssVariableEntryParser.parse(
            """[data-theme="dark"] { --bg: black; --fg: white; }"""
        )

        assertEquals(
            listOf(
                ParsedCssVariableEntry("--bg", "[data-theme=\"dark\"]", "black", "", line = 1),
                ParsedCssVariableEntry("--fg", "[data-theme=\"dark\"]", "white", "", line = 1)
            ),
            entries
        )
    }

    // Phase 8b — line-number emission. The hover popup's Source column shows
    // `file.css:N` so the user can jump from "which declaration applies?" to
    // the exact location. Line numbers are 1-based matching every editor UI.
    @Test
    fun `emits 1-based line number for each declaration`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --a: 1;
              --b: 2;
              --c: 3;
            }
            """.trimIndent()
        )

        assertEquals(listOf(2, 3, 4), entries.map { it.line })
    }

    // Phase 8b — multi-line values. When a value wraps over several lines,
    // the reported line is the one where `--name:` opens, not where the `;`
    // closes it. That's the line the user cares about when navigating.
    @Test
    fun `multi-line value records line where declaration opens`() {
        val entries = CssVariableEntryParser.parse(
            """
            :root {
              --hero-shadow:
                0 8px 24px rgba(0, 0, 0, 0.12),
                0 2px 6px rgba(0, 0, 0, 0.08);
            }
            """.trimIndent()
        )

        assertEquals(1, entries.size)
        assertEquals(2, entries.single().line)
    }
}
