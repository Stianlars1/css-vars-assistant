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
            listOf(ParsedCssVariableEntry("--layout-gap", "(min-width: 768px)", "24px", "")),
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
                    ""
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
                    ""
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
                    ""
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
            listOf(ParsedCssVariableEntry("--primary", "default", "#0000ff", "blue")),
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
            listOf(ParsedCssVariableEntry("--size", "default", "1px", "")),
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
            listOf(ParsedCssVariableEntry("--size", "default", "1px", "")),
            entries
        )
    }
}
