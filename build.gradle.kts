import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.stianlarsen"
version = "1.5.0"

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

// Configuration cache compatible version task that increments on every build
abstract class AutoIncrementVersionTask : DefaultTask() {

    @get:OutputFile
    abstract val indexVersionFile: RegularFileProperty

    @get:Internal  // Internal property, not an input
    abstract val buildCounterFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val providers: ProviderFactory

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        // Always run this task (don't cache it)
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun incrementVersion() {
        val (newVersion, versionInfo) = getVersionInfo()

        val newContent = """package cssvarsassistant.index

const val INDEX_VERSION = $newVersion"""

        indexVersionFile.asFile.get().writeText(newContent)
        println("🔄 INDEX_VERSION updated to $newVersion ($versionInfo)")
    }

    private fun getVersionInfo(): Pair<Int, String> {
        return try {
            // Try Git-based versioning first, but add build counter for uniqueness
            val commitCountOutput = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
                standardOutput = commitCountOutput
            }
            val commitCount = commitCountOutput.toString().trim().toInt()

            val branchOutput = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
                standardOutput = branchOutput
            }
            val branch = branchOutput.toString().trim()

            // Get and increment build counter
            val counterFile = buildCounterFile.asFile.get()
            val buildCounter = if (counterFile.exists()) {
                counterFile.readText().trim().toIntOrNull() ?: 0
            } else {
                // Create parent directory if it doesn't exist
                counterFile.parentFile.mkdirs()
                0
            }
            val newBuildCounter = buildCounter + 1
            counterFile.writeText(newBuildCounter.toString())

            // Use git commit count + base version + build counter for unique builds
            val baseVersion = 500
            val gitVersion = baseVersion + commitCount + newBuildCounter

            Pair(gitVersion, "Git-based (commits: $commitCount, build: $newBuildCounter, branch: $branch)")
        } catch (e: Exception) {
            // Fallback to file-based increment if Git is not available
            val indexFile = indexVersionFile.asFile.get()

            if (indexFile.exists()) {
                val content = indexFile.readText()
                val currentVersion = Regex("""const val INDEX_VERSION = (\d+)""")
                    .find(content)?.groupValues?.get(1)?.toInt() ?: 300
                val newVersion = currentVersion + 1
                Pair(newVersion, "File-based increment (previous: $currentVersion)")
            } else {
                Pair(300, "Default fallback")
            }
        }
    }
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
  Supercharge your CSS custom properties and pre-processor variables in JetBrains IDEs with advanced autocomplete, rich documentation, and powerful debugging tools.
</p>
<ul>
  <li><b>Instant variable lookup</b> – blazing-fast completions for <code>CSS</code>, <code>SCSS</code>, <code>SASS</code> and <code>LESS</code> variables.</li>
  <li><b>Smart autocomplete</b> – context-aware suggestions inside <code>var(--…)</code>, <code>@less</code> and <code>${'$'}scss</code>, sorted by value or context.</li>
  <li><b>Rich documentation pop-ups</b> – value tables (px equivalents), context labels, colour swatches with contrast info, plus <i>dynamic</i> <code>px Eq.</code>, <code>Hex</code> and <code>WCAG</code> columns that appear only when relevant. :contentReference[oaicite:3]{index=3}</li>
  <li><b>IntelliJ 2024.1+ API support</b> – leverages the new docs API for richer pop-ups, with graceful fallback for older IDEs.</li>
  <li><b>Derived-variable indicator</b> – alias / recursive completions are marked with <code>↗</code> so you instantly know the value is inherited. :contentReference[oaicite:4]{index=4}</li>
  <li><b>Advanced <code>@import</code> resolution</b> – follows and indexes nested imports with depth and scope controls.</li>
  <li><b>Debugging tools</b> – visual tracing of variable origins and import chains via the “Debug CSS Import Resolution” action.</li>
  <li><b>Configurable sorting &amp; ranking</b> – numeric value order (asc/desc) and logical context ranking (Default → Dark / media queries).</li>
  <li><b>Performance &amp; robustness</b> – centralised index versioning, smarter caching and race-condition fixes keep everything fast in large projects.</li>
</ul>
<p>
  <b>New in 1.5.0:</b> IntelliJ 2024.1+ docs, dynamic table columns, <code>↗</code> badge, better import tracing, cascade-aware values, and major performance refactors.
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
<h2>1.5.0 – 2025-06-15</h2>

<h3>Added</h3>
<ul>
  <li><b>IntelliJ 2024.1+ documentation API</b> – rich pop-ups with graceful fallback for older builds.</li>
  <li><b>Dynamic value-table columns</b> – docs auto-add <i>px Eq.</i>, <i>Hex</i> and <i>WCAG</i> when relevant.</li>
  <li><b>↗ Derived-value indicator</b> for alias / recursive completions.</li>
  <li>Improved DebugImportResolution helper for tracing variable origins and import chains.</li>
</ul>

<h3>Changed</h3>
<ul>
  <li><b>Cascade-aware logic</b> – completions and docs now use the last value per context.</li>
  <li>Smarter context labels, media-query parsing, colour handling and documentation rendering.</li>
  <li>Centralised index versioning, improved caching and overall maintainability.</li>
</ul>

<h3>Fixed</h3>
<ul>
  <li>Minor arithmetic-resolution bugs in pre-processors.</li>
  <li>Improved context collapsing, colour parsing and miscellaneous documentation issues.</li>
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
    // Register the version increment task
    val autoIncrementVersion = register<AutoIncrementVersionTask>("autoIncrementVersion") {
        group = "versioning"
        description = "Automatically increments INDEX_VERSION for both local dev and production"

        indexVersionFile.set(layout.projectDirectory.file("src/main/kotlin/cssvarsassistant/index/indexVersion.kt"))
        buildCounterFile.set(layout.projectDirectory.file(".gradle/build-counter.txt"))
    }

    withType<KotlinCompile> {
        // Run version increment before compilation
        dependsOn(autoIncrementVersion)

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
        // Ensure version is updated before building plugin
        dependsOn(autoIncrementVersion)

        from(fileTree("lib")) {
            exclude("kotlin-stdlib*.jar")
        }
    }

    // Also update version before running IDE (if you use runIde)
    named("runIde") {
        dependsOn(autoIncrementVersion)
    }
}