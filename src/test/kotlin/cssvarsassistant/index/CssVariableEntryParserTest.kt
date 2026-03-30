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
}
