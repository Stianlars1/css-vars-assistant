package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals

class VariableValuePositionTest {
    @Test
    fun `property values may continue on the next line`() {
        assertPosition("scss", ".a { color:\n  <caret>\$brand; }", true)
        assertPosition("less", ".a { color:\n  <caret>@brand; }", true)
        assertPosition("sass", ".a\n  color:\n    <caret>\$brand", true)
        assertPosition("scss", "\$alias:\n  <caret>\$brand;", true)
        assertPosition("scss", ".a { color: theme.<caret>\$brand; }", true)
    }

    @Test
    fun `declaration names and selectors are not value positions`() {
        assertPosition("scss", "<caret>\$brand: red;", false)
        assertPosition("less", ".a { <caret>@brand: red; }", false)
        assertPosition("sass", ".a\n  color: red\n  <caret>\$brand: blue", false)
        assertPosition("scss", ".a:hover <caret>\$brand { color: red; }", false)
    }

    @Test
    fun `dialects restrict variable sigils`() {
        assertPosition("css", ".a { color: <caret>\$brand; }", false)
        assertPosition("scss", ".a { color: <caret>@brand; }", false)
        assertPosition("less", ".a { color: <caret>\$brand; }", false)
        assertPosition("SCSS", ".a { color: <caret>\$brand; }", true)
    }

    @Test
    fun `comments and literal strings are excluded`() {
        assertPosition("less", ".a { /* color: <caret>@brand */ }", false)
        assertPosition("scss", ".a { color: red; // <caret>\$brand\n }", false)
        assertPosition("scss", ".a { content: '<caret>\$brand'; }", false)
        assertPosition("less", ".a { content: \"<caret>@brand\"; }", false)
        assertPosition("scss", ".a { content: '\\#{<caret>\$brand}'; }", false)
        assertPosition("scss", ".a { content: \\<caret>\$brand; }", false)
    }

    @Test
    fun `interpolation is code even inside a quoted string`() {
        assertPosition("scss", ".a { content: '#{<caret>\$brand}'; }", true)
        assertPosition("scss", ".#{<caret>\$brand} { color: red; }", true)
        assertPosition("less", ".a { content: '<caret>@{brand}'; }", true)
        assertPosition("scss", ".a { content: '#{\$brand} <caret>\$literal'; }", false)
        assertPosition("scss", ".a { content: '#{fn(\"<caret>\$literal\")}'; }", false)
    }

    @Test
    fun `sass expression directives allow variable arguments`() {
        assertPosition("scss", "@include spacing(<caret>\$brand);", true)
        assertPosition("scss", "@include spacing(<caret>\$brand) { color: red; }", true)
        assertPosition("scss", "@return <caret>\$brand;", true)
    }

    @Test
    fun `type selector pseudo classes are not property values`() {
        assertPosition("scss", "a:hover <caret>\$brand { color: red; }", false)
        assertPosition("less", "a:hover <caret>@brand { color: red; }", false)
        assertPosition("scss", ".outer { a:hover <caret>\$brand { color: red; } }", false)
        assertPosition("less", ".outer { a:hover <caret>@brand { color: red; } }", false)
        assertPosition("scss", ".a { color:<caret>\$brand; }", true)
        assertPosition("less", ".a { color:<caret>@brand; }", true)
    }

    @Test
    fun `quoted interpolation comments are excluded without hiding the next expression`() {
        assertPosition("scss", ".a { content: '#{/* <caret>\$brand */}'; }", false)
        assertPosition("scss", ".a { content: '#{\$real /* <caret>\$example */}'; }", false)
        assertPosition("scss", ".a { content: '#{// <caret>\$example\n}'; }", false)
        assertPosition("scss", ".a { content: '#{/* \$example */ <caret>\$real}'; }", true)
        assertPosition("scss", ".a { content: '#{// \$example\n<caret>\$real}'; }", true)
        assertPosition("scss", ".a { content: '#{\"/* literal */\" + <caret>\$real}'; }", true)
    }

    private fun assertPosition(extension: String, marked: String, expected: Boolean) {
        val offset = marked.indexOf("<caret>")
        val text = marked.replace("<caret>", "")
        assertEquals(expected, VariablePsiContext.isPreprocessorValuePosition(extension, text, offset), marked)
    }
}
