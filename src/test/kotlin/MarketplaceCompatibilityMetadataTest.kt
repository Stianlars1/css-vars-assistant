package cssvarsassistant.metadata

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarketplaceCompatibilityMetadataTest {

    private val projectRoot: Path = Path.of("").toAbsolutePath()

    @Test
    fun `plugin manifest declares javascript and css dependencies`() {
        val pluginXml = projectRoot.resolve("src/main/resources/META-INF/plugin.xml").toFile()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pluginXml)

        val dependencies = buildSet {
            val nodes = document.getElementsByTagName("depends")
            for (i in 0 until nodes.length) {
                add(nodes.item(i).textContent.trim())
            }
        }

        assertTrue("com.intellij.modules.platform" in dependencies)
        assertTrue("com.intellij.modules.lang" in dependencies)
        assertTrue("JavaScript" in dependencies)
        assertTrue("com.intellij.css" in dependencies)
    }

    @Test
    fun `plugin manifest limits completion contributors to stylesheet languages`() {
        val pluginXml = projectRoot.resolve("src/main/resources/META-INF/plugin.xml").toFile()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pluginXml)

        val languages = buildSet {
            val nodes = document.getElementsByTagName("completion.contributor")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val language = node.attributes?.getNamedItem("language")?.textContent?.trim()
                if (!language.isNullOrBlank()) {
                    add(language)
                }
            }
        }

        assertTrue("CSS" in languages)
        assertTrue("SCSS" in languages)
        assertTrue("Sass" in languages)
        assertTrue("LESS" in languages)
        assertFalse("JavaScript" in languages)
        assertFalse("TypeScript" in languages)
        assertFalse("JSX Harmony" in languages)
        assertFalse("TypeScript JSX" in languages)
    }

    @Test
    fun `build script targets broader ide family and verifier coverage`() {
        val buildScript = Files.readString(projectRoot.resolve("build.gradle.kts"))

        assertTrue("intellijIdeaUltimate(\"2025.1\")" in buildScript)
        assertTrue("id(\"org.jetbrains.intellij.platform\") version \"2.13.1\"" in buildScript)
        assertTrue("bundledPlugin(\"JavaScript\")" in buildScript)
        assertTrue("bundledPlugin(\"com.intellij.css\")" in buildScript)
        assertTrue("sinceBuild = \"251\"" in buildScript)
        assertTrue("JavaLanguageVersion.of(21)" in buildScript)
        assertTrue("jvmTarget.set(JvmTarget.JVM_21)" in buildScript)
        assertTrue("languageVersion.set(KotlinVersion.KOTLIN_2_1)" in buildScript)
        assertTrue("apiVersion.set(KotlinVersion.KOTLIN_2_1)" in buildScript)
        assertTrue("select {" in buildScript)
        assertTrue("IntelliJPlatformType.IntellijIdeaUltimate" in buildScript)
        assertTrue("IntelliJPlatformType.WebStorm" in buildScript)
        assertTrue("IntelliJPlatformType.GoLand" in buildScript)
        assertTrue("IntelliJPlatformType.PhpStorm" in buildScript)
        assertFalse("dependsOn(autoIncrementVersion)" in buildScript)
    }
}
