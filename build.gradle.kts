import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.stianlarsen"
version = "1.4.0"



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
              Enhances CSS variable usage with autocomplete and documentation.
              Shows <strong>variable values</strong> in completion suggestions <strong>on typing</strong> and 
              documentation on hover. Also displays color swatches for color variables.
            </p>
            <p>
              Supercharge your CSS custom properties in WebStorm and IntelliJ-based IDEs:
            </p>
            <ul>
              <li><b>Smart Autocomplete</b> inside <code>var(--…)</code></li>
              <li><b>Quick Documentation</b> (Ctrl+Q) showing value, description & color swatch</li>
              <li><b>JSDoc‑style</b> comment support (<code>@name</code>, <code>@description</code>, <code>@example</code>)</li>
              <li><b>Advanced @import resolution</b> with configurable scope and depth</li>
              <li><b>Multi-preprocessor support</b> (CSS, SCSS, SASS, LESS)</li>
            </ul>
            <p>
              Works in CSS, SCSS, SASS, LESS, JavaScript/TypeScript, and JSX/TSX files.
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
<h2>1.4.0 – 2025-06-02</h2>

<h3>Added</h3>
<ul>
  <li><strong>Global scope by default</strong> - the assistant now indexes <code>node_modules</code> out of the box, so external design-system variables resolve automatically.</li>
  <li><strong>LESS arithmetic evaluation</strong> - expressions such as <code>@lessVar-spacing-md - (@lessVar-spacing * 3)</code> are calculated (e.g. <code>24px</code>) in both completion and quick-docs.</li>
  <li><strong>One-time settings migrator</strong> - keeps the previous scope for existing users while new projects start with the global scope.</li>
</ul>

<h3>Changed</h3>
<ul>
  <li>File-based index version bumped to <code>95</code> - a safe re-index is triggered automatically after upgrade.</li>
  <li>Internal cache - import-resolution tweaks improve performance on large monorepos.</li>
</ul>

<h3>Fixed</h3>
<ul>
  <li>Completion and documentation no longer show raw aliases like <code>@lessVar-spacing-md</code> - they always display the final literal value.</li>
  <li>Stability improvements for deep import chains and long-running background indexing tasks.</li>
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
