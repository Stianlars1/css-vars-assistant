import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij.platform") version "2.13.1"
    kotlin("jvm") version "2.1.20"

}

group = "com.stianlarsen"
version = "1.8.6"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Use a broad commercial IntelliJ Platform base so Marketplace compatibility
        // is driven by the declared bundled plugin dependencies, not a WebStorm-only target.
        intellijIdeaUltimate("2025.1")
        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.css")
        testFramework(TestFrameworkType.Platform)

        // Add verification tools (instrumentationTools() is deprecated and removed)
        pluginVerifier()
        zipSigner()

    }
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    sandboxContainer.set(layout.buildDirectory.dir("sandbox"))
    buildSearchableOptions = false

    signing {
        certificateChain.set(providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY"))
        password.set(providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN"))
    }

    pluginConfiguration {
        id = "cssvarsassistant"
        name = "CSS Variables Assistant"
        version = project.version.toString()
        vendor {
            name = "StianLarsen"
            email = "stian.larsen@mac.com"
            url = "https://github.com/stianlars1/css-vars-assistant"
        }

        ideaVersion {
            sinceBuild = "251"
        }

        description = """
<h2>The fastest way to work with CSS custom properties &amp; design tokens in JetBrains IDEs</h2>

<p>
  <b>CSS Variables Assistant</b> turns <code>var(--my-token)</code> into a first-class citizen in WebStorm, IntelliJ IDEA Ultimate, PhpStorm, PyCharm Professional, GoLand and RubyMine. Get instant autocomplete, rich inline documentation with colour swatches and pixel equivalents, and full resolution of design-token <code>@import</code> chains across your project and <code>node_modules</code>.
</p>

<p>
  <b>Built for modern CSS workflows:</b> Tailwind layers, shadcn/ui tokens, Radix colours, Material tokens, CSS-in-JS bridge files, design-system packages, multi-theme setups (light / dark / high-contrast via media queries), SCSS &amp; LESS &amp; SASS.
</p>

<h3>Why developers install it</h3>
<ul>
  <li><b>Smart autocomplete inside <code>var(--…)</code></b> – your entire token catalog surfaces the moment you open a <code>var()</code> call. Works in CSS, SCSS, SASS and LESS files.</li>
  <li><b>Instant in-place documentation</b> – hover any <code>--token</code> for a rich popup showing the resolved value, colour swatch, HSB / hex, pixel equivalent for <code>rem</code> / <code>em</code> / <code>%</code> / <code>vh</code> / <code>vw</code>, WCAG AA / AAA contrast ratio, and the full <code>@import</code> resolution chain.</li>
  <li><b>Follows <code>@import</code> chains automatically</b> – indexes variables defined in design-token packages inside <code>node_modules</code>, with configurable depth and scope controls so you only pay for the resolution you need.</li>
  <li><b>Understands media-query context</b> – tokens redefined under <code>@media (prefers-color-scheme: dark)</code>, <code>min-width</code> breakpoints or any other context are shown side-by-side so you can compare values at a glance.</li>
  <li><b>Three-option sort order (new in 1.8.0)</b> – sort completions <i>by value ascending</i>, <i>by value descending</i>, or <i>alphabetically</i>. Applies consistently whether the popup opens at <code>var(--|)</code> or mid-prefix.</li>
  <li><b>JSDoc-style comments</b> – parse <code>@name</code>, <code>@description</code>, <code>@example</code> from block comments above your tokens and render them in the popup.</li>
  <li><b>Derived-variable marker</b> – alias / recursive completions get a <code>↗</code> badge so you know at a glance which values were resolved through references.</li>
  <li><b>Debug import chain</b> – a dedicated context-menu action prints the full import resolution tree for any CSS file. Answers "where does this token actually come from?" in one click.</li>
  <li><b>Customizable documentation columns</b> – show or hide <i>Context</i>, <i>Color Swatch</i>, <i>Value</i>, <i>Type</i>, <i>Source</i>, <i>Pixel Equivalent</i>, <i>Hex</i>, <i>WCAG Contrast</i> independently.</li>
  <li><b>Fast in large projects</b> – dedicated file-based index, weak-keyed caches, and scope-aware resolution keep completion under 100 ms even on monorepos with thousands of variables.</li>
</ul>

<h3>Keywords</h3>
<p>
  CSS variables, CSS custom properties, design tokens, <code>var(--token)</code>, <code>var()</code> autocomplete, Tailwind CSS, shadcn/ui, Radix UI, Radix Themes, Material Design tokens, MUI, Open Props, CSS cascade, <code>:root</code>, <code>calc()</code>, nested CSS variables, recursive variable resolution, dark mode tokens, theme variables, WebStorm CSS plugin, IntelliJ IDEA CSS autocomplete, JetBrains plugin design tokens, SCSS variables, Sass variables, LESS variables, <code>@import</code> resolution, JSDoc CSS, WCAG contrast checker, px equivalent, rem to px converter, hex to HSL, colour swatch, CSS-in-JS bridge.
</p>

<h3>✨ New in 1.8.6</h3>
<p>
  Bug-fix release closing <a href="https://github.com/Stianlars1/css-vars-assistant/issues/22">issue #22</a> reported by <a href="https://github.com/LordMaddhi">@LordMaddhi</a>. 8-digit hex colors with alpha — the modern CSS Color Level 4 <code>#RRGGBBAA</code> syntax — are now handled correctly.
</p>
<ul>
  <li><b>8-digit hex colors with alpha:</b> a value like <code>--accent: #7F80FF1A</code> no longer renders as a truncated <code>#80FF1A</code> in the hover popup's Hex column. Two compounding bugs were fixed: the parser was reading alpha as the <i>first</i> byte (Java <code>#AARRGGBB</code> packed-int order) instead of the <i>last</i> byte per the CSS spec, and the formatter was unconditionally dropping alpha. The canonical hex output now preserves alpha when present, and 4-digit <code>#RGBA</code> shorthand is recognised. Color swatches use <code>rgba()</code> so semi-transparent colors preview accurately.</li>
  <li>Fully-opaque colors still produce 6-digit hex (<code>#1E90FF</code>), so existing snapshots and the WebAIM contrast-checker URL are unaffected.</li>
  <li>Regression coverage in <code>ColorParserTest</code>: primary <code>#7F80FF1A</code> round-trip, 4-digit shorthand, full-opacity collapse-to-6-digit, fully-transparent round-trip, and explicit channel-position assertions verifying alpha is read from the LAST byte.</li>
</ul>

<h3>Previously in 1.8.5</h3>
<p>
  Bug-fix release closing <a href="https://github.com/Stianlars1/css-vars-assistant/issues/21">issue #21</a> reported by <a href="https://github.com/kolkinn">@kolkinn</a>. Calculated tokens that reference multi-valued nested variables — the Radix Themes pattern <code>--space-2: calc(8px * var(--scaling))</code> — are now displayed correctly in the hover popup.
</p>
<ul>
  <li><b>Calc + ambiguous nested var fix:</b> hovering <code>var(--space-2)</code> in a Radix Themes project no longer shows bare <code>0.9</code> (type: number). When the inner variable has multiple non-uniform values across themed selectors and no <code>:root</code> anchor, the resolver now leaves the <code>var(--scaling)</code> reference intact so the popup displays <code>calc(8px * var(--scaling))</code> verbatim. Deterministic single-default substitutions are unchanged.</li>
  <li><b>Calc wrapper preservation:</b> the recursive substitution path also stopped silently dropping surrounding text. <code>--gap: calc(var(--unit) * 2)</code> with <code>--unit: 4px</code> now correctly resolves to <code>calc(4px * 2)</code> rather than just <code>4px</code>.</li>
  <li>Four regression tests in <code>DocHelpersResolveVarValueTest</code> + a copy-paste reproducer fixture under <code>samples/issue-21/</code> for manual side-loaded verification.</li>
</ul>

<h3>Previously in 1.8.4</h3>
<p>
  Closed <a href="https://github.com/Stianlars1/css-vars-assistant/issues/20">issue #20</a> reported by <a href="https://github.com/Blinks44">@Blinks44</a>.
</p>
<ul>
  <li><b>Exact-match ranking:</b> typing <code>var(--sidebar-accent-foreground)</code> in full no longer left <code>--sidebar</code> selected at the top of the popup. The exact match is now always promoted, so pressing Enter/Tab doesn't overwrite what you already typed.</li>
</ul>

<h3>Previously in 1.8.3</h3>
<p>
  Follow-up to 1.8.1 issue <a href="https://github.com/Stianlars1/css-vars-assistant/issues/19">#19</a> — polish for design systems with many themes.
</p>
<ul>
  <li><b>Readable theme labels</b> (new setting, default on): <code>[data-theme="catppuccin"]</code> now renders as "Catppuccin", <code>[data-theme="high-contrast"]</code> as "High contrast", <code>.dark</code> as "Dark" — same applies to any single attribute-equals or single-class selector. Full raw selector available on hover. Turn off for paste-back-to-CSS fidelity.</li>
  <li><b>Merge duplicate-value rows (new setting, default on):</b> when several themes (catppuccin, sepia, matcha, …) resolve the same token to the same value, they're collapsed into one row whose Context column lists every contributing theme. Turn off to see one row per selector.</li>
  <li>Compound selectors, pseudo-classes, and IDs stay verbatim — prettifying them would misrepresent CSS semantics.</li>
</ul>

<h3>Previously in 1.8.2</h3>
<p>
  Quality-of-life polish on the hover popup. Long Source cells like <code>variables.css:230</code> used to wrap onto two lines because IntelliJ's documentation popup clamps its max width; 1.8.2 both nudges the popup wider and adds a compact-source-column preference so you can pick the look you prefer.
</p>
<ul>
  <li><b>Compact Source column (on by default):</b> renders just <code>:220</code> in the cell; the full <code>variables.css:220</code> shows on hover. Toggle under <i>Settings → Tools → CSS Variables Assistant → Documentation Popup Columns</i>.</li>
  <li><b>Wider popup by default:</b> the value table now asks the popup for at least ~700 px, so multi-column rows (Context, Value, Type, Source, Hex, WCAG) no longer wrap in the common case.</li>
  <li>Zero behaviour change to completion, indexing, or the v1.8.1 theming-selector rows.</li>
</ul>

<h3>Previously in 1.8.1</h3>
<p>
  Theming-focused follow-up. Closes issue
  <a href="https://github.com/Stianlars1/css-vars-assistant/issues/19">#19</a>
  reported by <a href="https://github.com/LordMaddhi">@LordMaddhi</a> and ships a related cascade-visibility improvement.
</p>
<ul>
  <li><b>Attribute and class theme selectors become their own rows:</b> <code>[data-theme="dark"]</code>, <code>.dark</code>, <code>.theme-high-contrast</code>, <code>:hover</code>, <code>[dir="rtl"]</code> and similar non-root selectors are now indexed as distinct contexts in the hover popup instead of collapsing into <code>default</code>. Perfect for shadcn/ui, Radix, Tailwind's <code>.dark</code> mode, and hand-rolled theme systems.</li>
  <li><b>Nested <code>@media</code> + selector blocks combine labels:</b> <code>@media (prefers-color-scheme: dark) { .hc { ... } }</code> shows up as <code>(prefers-color-scheme: dark) .hc</code>.</li>
  <li><b>Source column now shows <code>file.css:line</code></b> for every declaration, so when <code>--bg</code> is redefined across multiple theme files you can see exactly which file and line each value comes from.</li>
  <li><b>Cascade-ambiguity disclaimer</b> under multi-row value tables makes it explicit that runtime behaviour still depends on stylesheet load order and specificity.</li>
  <li>Index rebuilds once on first launch — the packed index record gained a 4th field (source line).</li>
</ul>

<h3>Previously in 1.8.0</h3>
<p>
  Closed issue
  <a href="https://github.com/Stianlars1/css-vars-assistant/issues/18">#18</a>
  and ~15 related completion-popup regressions that only surfaced during real IDE use. Highlights:
</p>
<ul>
  <li>Completion popup no longer hides <code>--error</code> behind <code>--error-foreground</code>, no longer shows a JSDoc comment where the resolved value should be, and no longer gets sticky while you type.</li>
  <li>New <b>Alphabetical</b> sort option and fixed inconsistency where the ASC / DESC preference was only honoured in some popup flows.</li>
  <li>Two new <i>Display Options</i> settings let you hide or clamp the description next to each completion item.</li>
  <li>Minified CSS files that start with a copyright comment are now indexed correctly (thanks to community contributor <a href="https://github.com/pierreoa">@pierreoa</a>).</li>
</ul>

<h3>Pairs well with</h3>
<p>
  <a href="https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints"><b>PxPeek — CSS Pixel Hints</b></a>:
  sibling plugin by the same author that shows inline pixel-equivalent hints next to every <code>rem</code>, <code>em</code>, <code>vh</code>, <code>%</code> and modern viewport unit in your editor. Zero-config, faster than flipping to DevTools.
</p>

<h3>Feedback &amp; issues</h3>
<p>
  Found a bug? Please open it at
  <a href="https://github.com/Stianlars1/css-vars-assistant/issues">github.com/Stianlars1/css-vars-assistant/issues</a>
  — reported bugs are usually fixed within a day. Every bit of feedback helps shape the plugin.
</p>
""".trimIndent()

        changeNotes = """
<h2>1.8.6 – 2026-04-29</h2>
<h3>Fixed</h3>
<ul>
  <li><b>8-digit hex colors with alpha (issue <a href="https://github.com/Stianlars1/css-vars-assistant/issues/22">#22</a>, reported by @LordMaddhi):</b> a value like <code>--accent: #7F80FF1A</code> (modern CSS Color Level 4 <code>#RRGGBBAA</code> syntax) was rendered in the hover popup's Hex column as <code>#80FF1A</code>, looking like the first two characters had been silently dropped. Two compounding bugs were responsible and have both been corrected: <code>ColorParser.parseHexColor</code> treated the 8-digit form as Java's <code>#AARRGGBB</code> packed-int order (alpha <i>first</i>) instead of the CSS spec's <code>#RRGGBBAA</code> (alpha <i>last</i>), and <code>colorToHex</code> always emitted six digits, unconditionally dropping any alpha that <i>had</i> been parsed. Combined, this produced the visible <code>#80FF1A</code> truncation. The parser now reads alpha from the last byte, the formatter preserves alpha when present (<code>#7F80FF1A</code> round-trips identically), and 4-digit <code>#RGBA</code> shorthand is recognised.</li>
  <li><b>Color swatch uses rgb()/rgba() instead of hex,</b> so semi-transparent colors preview accurately. Locale is forced to <code>Locale.ROOT</code> so the alpha float always uses a "." decimal separator (CSS doesn't accept ","-separated numbers in non-US locales).</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>Fully-opaque colors still produce 6-digit hex (<code>#1E90FF</code>), so existing snapshots and the WebAIM contrast-checker URL are unaffected. The new <code>ColorParser.colorToRgbHex(color)</code> helper is available for callers that strictly need 6-digit RGB output.</li>
  <li>Regression coverage in <code>ColorParserTest</code>: primary <code>#7F80FF1A</code> round-trip, 4-digit shorthand <code>#RGBA</code> and <code>#RGBF</code>, full-opacity collapse-to-6-digit, fully-transparent round-trip, explicit channel-position assertions, plus the pre-existing rgba tests updated to assert the new alpha-preserving contract (<code>#FF008080</code> instead of <code>#FF0080</code>).</li>
  <li>No index rebuild or settings change required — pure parser/rendering fix.</li>
</ul>
<h2>1.8.5 – 2026-04-28</h2>
<h3>Fixed</h3>
<ul>
  <li><b>Calculated values with multi-valued nested vars (issue <a href="https://github.com/Stianlars1/css-vars-assistant/issues/21">#21</a>, reported by @kolkinn):</b> A Radix Themes token like <code>--space-2: calc(8px * var(--scaling))</code> was rendered in the hover popup as bare <code>0.9</code> (type: number) — the resolver was picking the first of five non-uniform <code>--scaling</code> definitions <i>and</i> dropping the surrounding <code>calc(...)</code> wrapper. Two intertwined bugs in <code>resolveVarValue</code>: (a) the recursive call passed only the inner value as the new raw, throwing away the calc context; (b) <code>firstOrNull()</code> blindly picked an arbitrary entry when no <code>:root</code>/<code>default</code> anchor existed and the candidates were non-uniform. The resolver now substitutes the matched <code>var(--ref)</code> token <i>in place</i> so <code>calc(...)</code> survives, and leaves the reference intact when the inner variable has multiple non-uniform values across selectors with no <code>default</code> to anchor to. Deterministic single-default lookups (e.g. <code>var(--unit)</code> with <code>:root { --unit: 4px }</code>) still substitute as before.</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>Regression coverage in <code>DocHelpersResolveVarValueTest</code>: ambiguous Radix-style scenario, all-entries-uniform shortcut, <code>:root</code>-anchored cascade, and the explicit "calc wrapper preserved" case.</li>
  <li>No index rebuild or settings change required.</li>
  <li>A copy-paste reproducer fixture lives at <code>samples/issue-21/radix-scaling.css</code> in the repo for side-loaded manual verification.</li>
</ul>
<h2>1.8.4 – 2026-04-22</h2>
<h3>Fixed</h3>
<ul>
  <li><b>Exact-match ranking in the completion popup (issue <a href="https://github.com/Stianlars1/css-vars-assistant/issues/20">#20</a>, reported by @Blinks44):</b> typing the complete variable name <code>var(--sidebar-accent-foreground)</code> left <code>--sidebar</code> selected at the top of the popup in 1.8.0-1.8.3, so pressing Enter/Tab overwrote the full name the user had already typed. Two independent paths in the ranking stack both compared each entry against its own truncated matched-prefix instead of the user's actual query; both now compare against the full query so the exact match wins.</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>Three layers of regression tests lock the fix in: matcher unit (<code>matchingDegree</code> score ordering), comparator unit (<code>compareQuerySpecificity</code> over a standalone Entry list), and full platform-fixture integration (completing <code>var(--sidebar-accent-foreground)</code> against a shadcn-style token file).</li>
  <li>No index rebuild or settings change required.</li>
</ul>
<h2>1.8.3 – 2026-04-22</h2>
<h3>Added</h3>
<ul>
  <li><b>Readable theme labels in the hover popup</b> (new setting, default on): <code>[data-theme="catppuccin"]</code> now renders as "Catppuccin" in the Context column, <code>[data-theme="high-contrast"]</code> as "High contrast", <code>.dark</code> as "Dark", and so on. Any single attribute-equals or single-class selector is humanised — kebab-case and snake_case values get spaces and sentence-cased; user-authored uppercase (RTL, WCAG, acronyms) is preserved. Full raw selector lives on hover via a tooltip. Toggle under <i>Settings → Tools → CSS Variables Assistant → Documentation Popup Columns → "Prettify theme selector labels"</i> — turn off for paste-back-to-CSS fidelity.</li>
  <li><b>Collapse rows with identical values</b> (new setting, default on): when several themes resolve a token to the same value (e.g. catppuccin, sepia, and matcha all leaving <code>--bg: white</code>), they're merged into one row whose Context column lists every contributing theme ("Light mode, Catppuccin, Sepia"). Keeps the popup scannable in design systems with many theme variants. Turn off under <i>Settings → Tools → CSS Variables Assistant → Documentation Popup Columns → "Collapse rows with identical values"</i> to see one row per selector as in 1.8.2.</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>Compound selectors (<code>.dark .nested</code>, <code>[a] .b</code>), pseudo-classes (<code>:hover</code>, <code>:not(.foo)</code>) and IDs (<code>#app</code>) stay verbatim — prettifying them would misrepresent their CSS semantics.</li>
  <li>No index rebuild required.</li>
</ul>
<h2>1.8.2 – 2026-04-22</h2>
<h3>Added</h3>
<ul>
  <li><b>Compact Source column (on by default):</b> the hover popup's <i>Source</i> cell now renders just <code>:220</code>; the full <code>variables.css:220</code> appears on hover. Keeps the popup narrow enough to avoid wrapping at IntelliJ's max-width clamp. Toggle under <i>Settings → Tools → CSS Variables Assistant → Documentation Popup Columns</i>.</li>
  <li><b>Wider popup by default:</b> the value table now hints at ~700 px minimum width via a `min-width` wrapper + invisible spacer, so multi-column rows (Context, Value, Type, Source, Hex, WCAG) don't wrap in the common case. Platform still caps around 950 px; users can manually resize the popup and the IDE remembers.</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>No index rebuild required. Purely a rendering / UI polish release.</li>
</ul>
<h2>1.8.1 – 2026-04-22</h2>
<h3>Added</h3>
<ul>
  <li><b>Theming selectors become their own rows in the hover popup (issue #19, reported by @LordMaddhi):</b> Declarations inside <code>[data-theme="dark"]</code>, <code>.dark</code>, <code>.theme-high-contrast</code>, <code>:hover</code>, <code>[dir="rtl"]</code> and other non-root selectors are now indexed as distinct contexts. Before 1.8.1 every non-<code>:root</code>/<code>:host</code>/<code>html</code>/<code>body</code>/<code>*</code> block collapsed into <code>default</code> and only the last declaration was visible, which hid dark-mode values from any theme system that didn't use <code>@media (prefers-color-scheme: dark)</code>. Nested blocks combine labels, so <code>@media (prefers-color-scheme: dark) { .hc { … } }</code> renders as <code>(prefers-color-scheme: dark) .hc</code>.</li>
  <li><b>Source file and line per declaration in the hover popup:</b> the <i>Source</i> column now shows <code>tokens.css:42</code> for every indexed row, so when the same variable is defined across multiple theme files (shadcn, Radix, Tailwind, Material) you can see which file and line each value lives on. Legacy index records fall back to the previous "first resolution step" label automatically.</li>
  <li><b>Cascade-ambiguity disclaimer:</b> a small note under multi-row value tables reminds that rows are shown in index order and the runtime-applied value still depends on stylesheet load order and specificity — the popup doesn't simulate the full CSS cascade engine.</li>
</ul>
<h3>Changed</h3>
<ul>
  <li><code>INDEX_VERSION</code> bumped to force a one-time re-index on first launch after upgrade. The packed record now carries a 4th field (1-based source line); legacy 3-part records decode safely with <code>line = -1</code> so stale caches never crash the popup.</li>
  <li>The documentation service uses <code>FileBasedIndex.processValues</code> instead of <code>getValues</code> so the <code>(VirtualFile, packed)</code> pair is available in one pass rather than an O(files × keys) re-query via <code>getContainingFiles</code>.</li>
</ul>
<h2>1.8.0 – 2026-04-22</h2>
<h3>Fixed</h3>
<ul>
  <li><b>Completion popup no longer loses suggestions during progressive typing (issue #18 follow-up):</b> Several regressions that only surfaced in real IDE sessions — <code>hsl(var(--err))</code> showing every variable, a sticky popup that ignored subsequent keystrokes, <code>--foreground</code> / <code>--error</code> hidden behind their <code>*-foreground</code> siblings, and <code>var(-&lt;caret&gt;)</code> filtering out every variable without an internal dash. All fixed.</li>
  <li><b>Value column no longer shows a comment (issue #18):</b> When a CSS custom property has a JSDoc/block comment above it, the completion popup now always shows the resolved value — it no longer scrapes a <code>--name: ...;</code> sample out of an unrelated comment block. Both completion <i>and</i> hover documentation respect the real cascade winner.</li>
  <li><b>Autocompletion no longer fires outside <code>var()</code> (issue #18):</b> The gate now tracks parenthesis depth from each <code>var(</code> to the caret, so typing after a closed <code>var(--x)</code> on the same line stops surfacing every indexed variable. Identifiers ending in <code>var</code> (e.g. <code>myvar(</code>) also no longer trigger completions.</li>
  <li><b>Inline <code>/* ... */</code> in values and declarations:</b> The indexer now strips inline CSS comments from captured values and still indexes variables even when a leading <code>/* comment */</code> and the declaration share one line.</li>
  <li><b>Scope leaks <code>node_modules</code> fewer places:</b> <code>PROJECT_ONLY</code> and <code>PROJECT_WITH_IMPORTS</code> scopes now consistently exclude <code>node_modules</code> at the CSS query layer, matching the settings panel's wording.</li>
  <li><b><code>DESC</code> completion sort no longer reverses the tier hierarchy:</b> A latent bug had <code>.reversed()</code> flipping every step including PREFIX / TOKEN_PREFIX / SUBSTRING tier ordering. Only the value-level comparison reverses now; tier ordering is always PREFIX first.</li>
</ul>
<h3>Added</h3>
<ul>
  <li><b>Three-option completion sort order:</b> <i>Completion sort order</i> now offers <b>By value ascending</b> (default), <b>By value descending</b>, and <b>Alphabetical by name</b>. The preference applies consistently in both the blank-query autopopup path and fresh invocations, so <code>--padding-sm</code> / <code>--padding-md</code> / <code>--padding-lg</code> always appear in the chosen order regardless of how the popup was opened.</li>
  <li><b>Completion description toggle + length:</b> New settings under <i>Display Options</i> let you hide the description that appears next to each completion item, or clamp it to a custom length (0 = hide, up to 120 chars).</li>
  <li><b>Optional feedback prompt:</b> After ~14 days and 20 uses of CSS variable completion, a single non-intrusive balloon asks if you'd like to rate the plugin on Marketplace. "Remind me later" and "Don't show again" actions included. Prompt conditions are conservative — it will never re-appear once dismissed.</li>
  <li><b>Pairs well with <a href="https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints">PxPeek</a>:</b> sibling plugin by the same author adding inline px-equivalents for <code>rem</code> / <code>em</code> / <code>vh</code> / <code>%</code> and all modern viewport units. Linked from the Marketplace description and the README.</li>
  <li><b>Broader verifier coverage:</b> Plugin verification now also runs against PyCharm Professional and RubyMine — both bundle the JavaScript and CSS plugins the plugin depends on.</li>
  <li><b>Regression tests:</b> New tests lock in each of the fixes above so the classes of bug in issue #18 don't come back.</li>
</ul>
<h3>Notes</h3>
<ul>
  <li>The CSS variable index will rebuild once on first launch after upgrade because the parser now captures values differently.</li>
</ul>
<h2>1.7.2 – 2026-04-15</h2>
<h3>Fixed</h3>
<ul>
  <li><b>Clean <code>var(--name)</code> insertion:</b> Selecting a completion inside <code>var(...)</code> now always produces the correct <code>var(--name)</code> — the 1.7.1 release could insert <code>var(----name)</code>, <code>var(---name)</code>, or a trailing dash (<code>var(--name-)</code>) depending on when the popup opened.</li>
  <li><b>Works inside any CSS function:</b> Fix also covers <code>var()</code> nested inside other CSS functions, e.g. <code>hsl(var(--bg))</code>, <code>rgba(var(--bg), 0.5)</code>, <code>color-mix(in oklch, var(--bg) 50%, white)</code>, <code>calc(var(--size) * 2)</code>, and <code>linear-gradient(hsl(var(--a)), hsl(var(--b)))</code>. Only the <code>var(...)</code> containing the caret is rewritten.</li>
</ul>
<h3>Added</h3>
<ul>
  <li><b>Insertion-based completion regression tests:</b> New tests assert the actual document state after selecting a completion (not just which items are offered), covering bare <code>var(...)</code> and <code>var()</code> nested inside <code>hsl</code> / <code>hsla</code> / <code>rgb</code> / <code>rgba</code> / <code>oklch</code> / <code>color-mix</code> / <code>calc</code> / <code>clamp</code> / <code>linear-gradient</code>, plus multi-<code>var()</code> declarations and non-<code>var()</code> contexts.</li>
</ul>
<h2>1.7.1 – 2026-04-08</h2>
<h3>Added</h3>
<ul>
  <li><b>Completion-scope regression coverage:</b> Added tests that lock CSS variable completion to stylesheet files and valid <code>var(...)</code> value contexts.</li>
</ul>
<h3>Fixed</h3>
<ul>
  <li><b>Stylesheet-only completion:</b> CSS variable completions are no longer registered in JavaScript, TypeScript, JSX, or TSX files.</li>
  <li><b>Value-context completion:</b> CSS variable suggestions now require a real <code>var(...)</code> context and no longer interfere with CSS property-name completion such as <code>background</code>.</li>
</ul>
""".trimIndent()
    }

    pluginVerification {
        ides {
            select {
                types = listOf(
                    IntelliJPlatformType.IntellijIdeaUltimate,
                    IntelliJPlatformType.WebStorm,
                    IntelliJPlatformType.GoLand,
                    IntelliJPlatformType.PhpStorm,
                    IntelliJPlatformType.PyCharmProfessional,
                    IntelliJPlatformType.RubyMine
                )
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "251"
                untilBuild = "251.*"
            }
        }

        // Suppress experimental API warnings while keeping all critical checks
        failureLevel.set(
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
                VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
                VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
                VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
                VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
                VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES
                // Note: Deliberately excluding EXPERIMENTAL_API_USAGES since we use
                // the JetBrains-recommended V2 Documentation API
            )
        )
    }
}


tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            languageVersion.set(KotlinVersion.KOTLIN_2_1)
            apiVersion.set(KotlinVersion.KOTLIN_2_1)
        }
    }

    withType<Test> {
        useJUnitPlatform()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    buildPlugin {
        from(fileTree("lib")) {
            exclude("kotlin-stdlib*.jar")
        }
    }
}
