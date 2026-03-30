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
version = "1.7.0"

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
  <b>✨ New in 1.7.0:</b> Expanded JetBrains IDE compatibility metadata, broader verifier coverage, and regression tests for Marketplace compatibility.
</p>
""".trimIndent()

        changeNotes = """
<h2>1.7.0 – 2026-03-29</h2>
<h3>Added</h3>
<ul>
  <li><b>Marketplace compatibility coverage:</b> Added regression tests to lock the plugin manifest and build metadata required for broader JetBrains IDE support.</li>
</ul>
<h3>Changed</h3>
<ul>
  <li><b>Broader IDE targeting:</b> The plugin now declares JavaScript + CSS dependencies explicitly and is built from a broader IntelliJ IDEA Ultimate base instead of a WebStorm-only target.</li>
  <li><b>Verification setup:</b> Plugin verification is now configured for IntelliJ IDEA Ultimate, WebStorm, GoLand, and PhpStorm.</li>
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
                    IntelliJPlatformType.PhpStorm
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
