package cssvarsassistant.audit

import cssvarsassistant.index.CssVariableEntryParser
import cssvarsassistant.index.CssVariableIndexValueCodec
import cssvarsassistant.index.PreprocessorVariableEntryParser
import cssvarsassistant.documentation.lastLocalValueInFile
import cssvarsassistant.util.CssTextUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserEdgeCaseAuditTest {
    @Test fun `https strings survive comment removal`() {
        val source = """:root { --image: url("https://example.com/Logo.svg"); }"""
        assertEquals(source, CssTextUtil.stripCssComments(source))
    }

    @Test fun `literal block comment markers in strings survive`() {
        val source = """:root { --label: "/* literal */"; }"""
        assertEquals(source, CssTextUtil.stripCssComments(source))
    }

    @Test fun `css URL is indexed intact`() {
        val entries = CssVariableEntryParser.parse(""":root { --image: url("https://example.com/Logo.svg"); }""", "css")
        assertEquals(listOf("url(\"https://example.com/Logo.svg\")"), entries.map { it.value })
    }

    @Test fun `quoted semicolon is part of the CSS value`() {
        val entries = CssVariableEntryParser.parse(""":root { --label: "left;right"; }""", "css")
        assertEquals(listOf("\"left;right\""), entries.map { it.value })
    }

    @Test fun `minified adjacent rules retain their own contexts`() {
        val entries = CssVariableEntryParser.parse(":root{--bg:white}.dark{--bg:black}", "css")
        assertEquals(listOf("default" to "white", ".dark" to "black"), entries.map { it.context to it.value })
    }

    @Test fun `declaration following multiline value on same line is retained`() {
        val entries = CssVariableEntryParser.parse(":root {\n --shadow:\n  0 1px black; --gap: 4px;\n}", "css")
        assertEquals(listOf("--shadow", "--gap"), entries.map { it.name })
    }

    @Test fun `multiline final declaration may omit semicolon`() {
        val entries = CssVariableEntryParser.parse(":root {\n --shadow:\n  0 1px black\n}", "css")
        assertEquals(listOf("0 1px black"), entries.map { it.value })
    }

    @Test fun `unicode custom property names are indexed`() {
        assertEquals(listOf("--farge-blå"), CssVariableEntryParser.parse(":root { --farge-blå: blue; }", "css").map { it.name })
    }

    @Test fun `sass indentation preserves theme context`() {
        val entries = CssVariableEntryParser.parse(":root\n  --bg: white\n.dark\n  --bg: black", "sass")
        assertEquals(listOf("default" to "white", ".dark" to "black"), entries.map { it.context to it.value })
    }

    @Test fun `string contents do not create phantom declarations`() {
        val entries = CssVariableEntryParser.parse(""":root { --label: "--phantom: red;"; }""", "css")
        assertEquals(listOf("--label" to "\"--phantom: red;\""), entries.map { it.name to it.value })
    }

    @Test fun `local lookup respects complete variable name`() {
        assertEquals(null, lastLocalValueInFile(":root { --prefix-gap: 9px; }", "--gap"))
    }

    @Test fun `local lookup accepts omitted final semicolon`() {
        assertEquals("4px", lastLocalValueInFile(":root { --gap: 4px }", "--gap"))
    }

    @Test fun `preprocessor quoted semicolon is retained`() {
        assertEquals("\"left;right\"", PreprocessorVariableEntryParser.parse("${'$'}label: \"left;right\";", "scss")["${'$'}label"])
    }

    @Test fun `sass default assignment does not overwrite an existing value`() {
        assertEquals("red", PreprocessorVariableEntryParser.parse("${'$'}brand: red;\n${'$'}brand: blue !default;", "scss")["${'$'}brand"])
    }

    @Test fun `quoted sass flag is literal text`() {
        assertEquals("\"keep !default\"", PreprocessorVariableEntryParser.parse("${'$'}label: \"keep !default\";", "scss")["${'$'}label"])
    }

    @Test fun `index codec round trips separators inside value`() {
        val encoded = CssVariableIndexValueCodec.encode("default", "\"a|||b\"", "description", 7)
        val decoded = CssVariableIndexValueCodec.decodePacked(encoded)
        assertEquals(1, decoded.size)
        assertEquals("\"a|||b\"", decoded.single().value)
    }

    @Test fun `index codec round trips separators inside documentation`() {
        val encoded = CssVariableIndexValueCodec.encode("default", "red", "@example a|||b", 7)
        val decoded = CssVariableIndexValueCodec.decodePacked(encoded)
        assertEquals("@example a|||b", decoded.single().comment)
        assertEquals(7, decoded.single().line)
    }

    @Test fun `ordinary comments do not add tokens`() {
        assertTrue(CssVariableEntryParser.parse("/* --hidden: red; */", "css").isEmpty())
    }
}
