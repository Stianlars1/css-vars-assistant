package cssvarsassistant.documentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrettifySelectorTest {

    // 1.8.3 — attribute-equals selectors are the most common theming pattern
    // (shadcn, Radix, Tailwind data-theme modes). Humanise the quoted value
    // and title-case the first letter.
    @Test
    fun `prettifies quoted attribute-equals selector`() {
        assertEquals("Catppuccin", prettifySelector("""[data-theme="catppuccin"]"""))
        assertEquals("Dark", prettifySelector("""[data-theme="dark"]"""))
    }

    @Test
    fun `prettifies single-quoted attribute-equals selector`() {
        assertEquals("Catppuccin", prettifySelector("[data-theme='catppuccin']"))
    }

    @Test
    fun `prettifies unquoted attribute-equals selector`() {
        assertEquals("Catppuccin", prettifySelector("[data-theme=catppuccin]"))
    }

    // Kebab-case values get spaces, sentence-cased. "high-contrast" is the
    // typical a11y theme name we expect to see.
    @Test
    fun `converts kebab-case value to spaced sentence case`() {
        assertEquals("High contrast", prettifySelector("""[data-theme="high-contrast"]"""))
        // `.theme-high-contrast-mode` stays as-is through the humaniser —
        // we don't strip a "theme-" prefix because some design systems use
        // "theme" as part of the actual theme name (e.g. ".theme-a", ".theme-b").
        assertEquals("Theme high contrast mode", prettifySelector(""".theme-high-contrast-mode"""))
    }

    @Test
    fun `converts snake_case value to spaced sentence case`() {
        assertEquals("High contrast", prettifySelector("""[data-theme="high_contrast"]"""))
    }

    // Common acronyms in theme keys (RTL direction, WCAG compliance mode)
    // should stay uppercase — the humaniser only title-cases when the first
    // letter is lower-case, so user-written caps survive intact.
    @Test
    fun `preserves uppercase acronyms`() {
        assertEquals("RTL", prettifySelector("""[dir="RTL"]"""))
        assertEquals("WCAG", prettifySelector(""".WCAG"""))
    }

    // Other attribute-selector operators ([data-foo~="bar"] etc.) are
    // valid CSS and the humaniser handles them — any shape that contains
    // a quoted value resolves to that value.
    @Test
    fun `handles attribute-contains and prefix match operators`() {
        assertEquals("Dark", prettifySelector("""[data-theme~="dark"]"""))
        assertEquals("Theme", prettifySelector("""[class^="theme"]"""))
    }

    // Single class without a dash is rendered as-is (just title-cased).
    @Test
    fun `prettifies simple class selector`() {
        assertEquals("Dark", prettifySelector(".dark"))
        assertEquals("Catppuccin", prettifySelector(".catppuccin"))
    }

    @Test
    fun `prettifies hyphenated class selector`() {
        assertEquals("Theme high contrast", prettifySelector(".theme-high-contrast"))
    }

    // Compound selectors (`.dark .nested`, `[a] .b`, `:hover .c`) are
    // intentionally NOT prettified — the humaniser only handles single
    // tokens. The raw selector stays verbatim so we never misrepresent
    // complex CSS relationships.
    @Test
    fun `returns null for compound selectors`() {
        assertNull(prettifySelector(".dark .nested"))
        assertNull(prettifySelector("""[data-theme="dark"] .card"""))
        assertNull(prettifySelector(".parent > .child"))
        assertNull(prettifySelector(".a, .b"))
    }

    // Pseudo-classes and IDs are skipped — their semantics change meaning
    // beyond simple theming (`#app`, `:hover`, `:focus-within` are not
    // "themes"), so rendering them verbatim communicates intent better.
    @Test
    fun `returns null for pseudo-classes and id selectors`() {
        assertNull(prettifySelector(":hover"))
        assertNull(prettifySelector(":not(.foo)"))
        assertNull(prettifySelector("#app"))
        assertNull(prettifySelector(":root"))
    }

    // Empty and nonsense inputs should fall through to null so the caller
    // can render the raw context. No exceptions, no partial prettifications.
    @Test
    fun `returns null for empty or non-selector input`() {
        assertNull(prettifySelector(""))
        assertNull(prettifySelector("(min-width: 768px)"))
        assertNull(prettifySelector("default"))
    }

    // Integration: contextLabel() is the public entry point. When the
    // selector is prettifiable, the label is prettified; when not, raw.
    @Test
    fun `contextLabel prettifies theme selectors through the same path`() {
        assertEquals("Catppuccin", contextLabel("""[data-theme="catppuccin"]""", isColor = false))
        assertEquals("Dark", contextLabel(".dark", isColor = false))
        assertEquals(".parent > .child", contextLabel(".parent > .child", isColor = false))
    }

    // 1.8.3 setting: `prettifyTheme = false` keeps the raw selector so
    // developers who prefer paste-back-to-CSS fidelity (or clearer
    // class-vs-attribute distinction at a glance) can opt out of humanisation.
    @Test
    fun `contextLabel keeps raw selectors when prettifyTheme is disabled`() {
        assertEquals(
            """[data-theme="catppuccin"]""",
            contextLabel("""[data-theme="catppuccin"]""", isColor = false, prettifyTheme = false)
        )
        assertEquals(".dark", contextLabel(".dark", isColor = false, prettifyTheme = false))
        // Media-query rendering still pretty-prints, regardless of the flag —
        // the flag only governs theme-shaped SELECTORS, not media queries.
        assertEquals(
            "Dark mode",
            contextLabel("(prefers-color-scheme: dark)", isColor = false, prettifyTheme = false)
        )
    }
}
