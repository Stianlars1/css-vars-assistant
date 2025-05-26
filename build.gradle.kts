import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.stianlarsen"
version = "1.2.5"



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
    <h2>1.2.0 – 2025‑05‑22</h2>

    <h3>Added</h3>
    <ul>
      <li><b>Advanced @import resolution:</b> intelligent resolution of <code>@import</code> statements across CSS, SCSS, SASS, and LESS files.</li>
      <li><b>Smart node_modules handling:</b> properly resolves scoped packages like <code>@company/package/path</code>.</li>
      <li><b>Configurable indexing scope:</b> choose between project‑only, project + imports, or full global indexing.</li>
      <li><b>Import depth control:</b> configurable maximum depth for <code>@import</code> chains to prevent infinite recursion.</li>
      <li><b>Enhanced settings panel:</b> fine‑tune plugin behavior with expanded configuration options.</li>
      <li><b>Debug tools:</b> new "Debug CSS Import Resolution" action for troubleshooting import chains.</li>
    </ul>

    <h3>Improved</h3>
    <ul>
      <li><b>Multi‑extension support:</b> automatically tries <code>.css</code>, <code>.scss</code>, <code>.sass</code>, <code>.less</code> extensions when resolving imports.</li>
      <li><b>Relative path resolution:</b> better handling of <code>./</code> and <code>../</code> import paths.</li>
      <li><b>Performance optimizations:</b> smarter file filtering and caching for faster completion suggestions.</li>
      <li><b>Error handling:</b> improved stability when processing malformed import statements.</li>
    </ul>

    <h3>Fixed</h3>
    <ul>
      <li>Import resolution now correctly distinguishes between relative paths and node_modules packages.</li>
      <li>Deprecated API usage replaced with modern IntelliJ Platform APIs.</li>
      <li>Better handling of circular import dependencies.</li>
      <li>Improved compatibility with latest WebStorm/IntelliJ versions.</li>
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
