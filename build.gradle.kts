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
version = "1.8.0"

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
  CSS variables, CSS custom properties, design tokens, <code>var(--token)</code>, Tailwind CSS, shadcn/ui, Radix, Material, CSS cascade, <code>:root</code>, WebStorm CSS plugin, IntelliJ CSS autocomplete, SCSS variables, LESS variables, SASS variables, @import resolution, WCAG contrast, px equivalent, rem to px, hex to HSL, colour swatch.
</p>

<h3>✨ New in 1.8.0</h3>
<p>
  Biggest quality release to date. Closes issue
  <a href="https://github.com/Stianlars1/css-vars-assistant/issues/18">#18</a>
  and ~15 related completion-popup regressions that only surfaced during real IDE use. Highlights:
</p>
<ul>
  <li>Completion popup no longer hides <code>--error</code> behind <code>--error-foreground</code>, no longer shows a JSDoc comment where the resolved value should be, and no longer gets sticky while you type.</li>
  <li>New <b>Alphabetical</b> sort option and fixed inconsistency where the ASC / DESC preference was only honoured in some popup flows.</li>
  <li>Two new <i>Display Options</i> settings let you hide or clamp the description next to each completion item.</li>
  <li>Minified CSS files that start with a copyright comment are now indexed correctly (thanks to community contributor <a href="https://github.com/pierreoa">@pierreoa</a>).</li>
  <li>14+ new regression tests lock in every fix.</li>
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
