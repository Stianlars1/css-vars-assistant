import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.stianlarsen"
version = "1.4.2"



repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        webstorm("2025.1")
        bundledPlugin("com.intellij.css")
    }
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
}

intellijPlatform {
    sandboxContainer.set(layout.buildDirectory.dir("sandbox"))
    buildSearchableOptions = false
    pluginConfiguration {
        id = "cssvarsassistant"
        name = "CSS Variables Assistant"
        version = project.version.toString()

        description = """
        <h2>CSS Variables Assistant</h2>
        <p>
          Supercharge your CSS custom properties in WebStorm and IntelliJ-based IDEs with advanced autocomplete, documentation, and preprocessor support.
        </p>
        <ul>
          <li><b>Instant variable lookup</b> – LESS and SCSS variables are now indexed for blazing-fast completions and documentation.</li>
          <li><b>Smart Autocomplete</b> – Context-aware suggestions inside <code>var(--…)</code>, <code>@less</code>, and <code>${'$'}scss</code> with value-based sorting (by px size, color, or number).</li>
          <li><b>Quick Documentation</b> (<kbd>Ctrl+Q</kbd>) – Shows value tables (with pixel equivalents for rem/em/%/vh/vw/pt), context labels (Default, Dark, min-width, etc.), and color swatches.</li>
          <li><b>JSDoc‑style</b> comment support – <code>@name</code>, <code>@description</code>, <code>@example</code> auto-parsed and displayed.</li>
          <li><b>Advanced @import resolution</b> – Traverses and indexes imports across CSS, SCSS, SASS & LESS, with configurable scope and max depth.</li>
          <li><b>Configurable sorting</b> – Completion list order is customizable: ascending or descending by value.</li>
          <li><b>Context ranking</b> – Contexts (Default, Light, Dark, min/max-width, etc.) are ranked for optimal relevance.</li>
          <li><b>Debugging tools</b> – Trace variable origins and import chains visually for easy debugging.</li>
          <li><b>Performance & robustness</b> – Sophisticated caching, race condition fixes, and extensive automated tests ensure fast, reliable operation even in large projects.</li>
          <li><b>Works everywhere</b> – CSS, SCSS, SASS, LESS.</li>
        </ul>
        <p>
          <b>New in 1.4.2:</b> Preprocessor variable index, value-based completion sorting, pixel equivalents in docs, smarter var() detection, and more!
        </p>
    """.trimIndent()

        vendor {
            name = "StianLarsen"
            email = "stian.larsen@mac.com"
            url = "https://github.com/stianlars1/css-vars-assistant"
        }

        ideaVersion {
            sinceBuild = "241"
        }

        changeNotes = """
<h2>1.4.2 – 2025-06-12</h2>

<h3>Added</h3>
<ul>
  <li><b>Preprocessor variable index</b>: LESS and SCSS variables are now indexed for instant look‑ups using a new <code>PreprocessorVariableIndex</code>.</li>
  <li><b>Value-based completion sorting</b>: Variable completions are sorted by their numeric value, with an option to choose ascending or descending order.</li>
  <li><b>Pixel equivalent column</b>: Variable documentation now shows the pixel equivalent for rem/em/%/vh/vw/pt values.</li>
  <li><b>Settings UI for sorting</b>: Added a configuration option to choose value-based variable sorting order (ascending or descending).</li>
  <li><b>Comprehensive value-type utilities</b>: Added <code>ValueUtil</code> for classifying, comparing, and converting variable values for size, color, and number types.</li>
  <li><b>Smarter context ranking</b>: Added <code>RankUtil</code> for logical sorting and ranking of context labels (e.g., default, min-width).</li>
  <li><b>Extensive automated tests</b>: Added <code>ValueUtilTest</code> and <code>RankingTest</code> for robust value handling and context ranking.</li>
</ul>

<h3>Changed</h3>
<ul>
  <li><b>Improved performance and scope caching</b>: Variable key cache and scope caching now use stable, scope-aware maps for reliable completion and documentation.</li>
  <li><b>Smarter var() detection</b>: Completion now works while typing incomplete <code>var(</code> and resolves aliases more quickly, even with missing parenthesis.</li>
  <li><b>Documentation improvements</b>: Documentation tables conditionally display pixel equivalents and improved context sorting, with more robust and readable HTML output.</li>
  <li><b>Refactored preprocessor resolution</b>: Preprocessor variable resolution is faster and now leverages the new index instead of scanning files directly.</li>
  <li><b>Improved logging and error handling</b>: More robust cancellation checks and error reporting in completion and documentation providers.</li>
  <li><b>Code cleanup</b>: Removed legacy and duplicated code, improved code comments, and enhanced maintainability.</li>
</ul>

<h3>Fixed</h3>
<ul>
  <li><b>Scope caching race conditions</b>: Caches for variable keys and preprocessor scopes are now properly invalidated and synchronized on changes, preventing duplicate completions and stale results.</li>
  <li><b>Duplicate IDE completions</b>: Improved completion logic eliminates repeated suggestions.</li>
  <li><b>Robustness</b>: Improved handling of edge cases in value parsing, prefix extraction, and context detection.</li>
  <li><b>IDE startup and indexing</b>: Now compatible with latest IntelliJ indexing and plugin APIs.</li>
</ul>

<h3>Removed</h3>
<ul>
  <li>Legacy file scan-based preprocessor variable resolution logic (now replaced by index-based resolution).</li>
  <li>Unused or obsolete code in tests and main sources.</li>
</ul>

""".trimIndent()


    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            languageVersion.set(KotlinVersion.KOTLIN_2_0)
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
