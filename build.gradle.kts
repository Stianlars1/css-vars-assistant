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
<h2>CSS Variables Assistant</h2>
<p>
  Supercharge your CSS custom properties and pre-processor variables in JetBrains IDEs with advanced autocomplete, rich documentation, and powerful debugging tools.
</p>
<ul>
  <li><b>Customizable Documentation Columns</b> – choose what you see (Context, Value, Source, etc.).</li>
  <li><b>Resolution Chain Tooltip</b> – hover to see the full resolution path of a variable.</li>
  <li><b>Instant variable lookup</b> – blazing-fast completions for <code>CSS</code>, <code>SCSS</code>, <code>SASS</code> and <code>LESS</code> variables.</li>
  <li><b>Smart autocomplete</b> – context-aware suggestions inside <code>var(--…)</code>, <code>@less</code> and <code>${'$'}scss</code>, sorted by value or context.</li>
  <li><b>Rich documentation pop-ups</b> – value tables (px equivalents), context labels, color swatches with contrast info, plus <i>dynamic</i> <code>px Eq.</code>, <code>Hex</code> and <code>WCAG</code> columns that appear only when relevant.</li>
  <li><b>IntelliJ 2025.1+ platform support</b> – targets the current 251 platform line with the modern documentation and completion APIs used by the plugin.</li>
  <li><b>Derived-variable indicator</b> – alias / recursive completions are marked with <code>↗</code> so you instantly know the value is inherited.</li>
  <li><b>JSDoc-style comments</b> – auto-parsing and display of <code>@name</code>, <code>@description</code>, and <code>@example</code>.</li>
  <li><b>Advanced <code>@import</code> resolution</b> – follows and indexes nested imports with depth and scope controls.</li>
  <li><b>Debugging tools</b> – visual tracing of variable origins and import chains via the "Debug CSS Import Resolution" action.</li>
  <li><b>Configurable sorting &amp; ranking</b> – numeric value order (asc/desc) and logical context ranking (Default → Dark / media queries).</li>
  <li><b>Performance &amp; robustness</b> – centralized index versioning, smarter caching and race-condition fixes keep everything fast in large projects.</li>
  <li><b>CSS cascade compliance</b> – documentation and completions now correctly follow CSS cascade rules where local declarations beat imports.</li>
  <li><b>Works everywhere</b> – <code>CSS</code>, <code>SCSS</code>, <code>SASS</code>, <code>LESS</code>.</li>
</ul>
<p>
  <b>✨ New in 1.8.0:</b> Fixes the completion regressions reported in issue
  #18. The popup no longer shows a comment's description in place of the
  real value, and CSS variables are no longer offered after a closed
  <code>var(...)</code> call on the same line. Plus two new settings so you
  can tune how descriptions appear next to each completion item.
</p>
<p>
  <b>Pairs well with:</b>
  <a href="https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints">PxPeek</a>
  — inline pixel-equivalent hints for every relative CSS unit.
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
