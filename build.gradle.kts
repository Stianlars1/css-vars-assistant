import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.stianlarsen"
version = "1.4.3.0"



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
          <b>New in 1.4.3:</b> Richer documentation with usage counts and dependencies, arithmetic for preprocessor variables and smarter context grouping.
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
<h2>1.4.3 – 2025-06-13</h2>

<h3>Added</h3>
<ul>
  <li><b>Rich variable documentation</b>: usage counts, dependencies and related variables.</li>
  <li><b>Preprocessor arithmetic</b>: expressions like <code>(@var * 1.5)</code> resolve with decimals and new units.</li>
  <li><b>Context grouping</b>: completions present the latest value for each context.</li>
</ul>

<h3>Changed</h3>
<ul>
  <li>Unified index version constant for all indexes.</li>
</ul>

<h3>Removed</h3>
<ul>
  <li>Legacy <code>PreprocessorUtil_old</code>.</li>
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
