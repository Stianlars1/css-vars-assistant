package cssvarsassistant.metadata

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
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
    fun `build script targets broader ide family and verifier coverage`() {
        val buildScript = Files.readString(projectRoot.resolve("build.gradle.kts"))

        assertTrue("intellijIdeaUltimate(\"2025.1\")" in buildScript)
        assertTrue("bundledPlugin(\"JavaScript\")" in buildScript)
        assertTrue("bundledPlugin(\"com.intellij.css\")" in buildScript)
        assertTrue("select {" in buildScript)
        assertTrue("IntelliJPlatformType.IntellijIdeaUltimate" in buildScript)
        assertTrue("IntelliJPlatformType.WebStorm" in buildScript)
        assertTrue("IntelliJPlatformType.GoLand" in buildScript)
        assertTrue("IntelliJPlatformType.PhpStorm" in buildScript)
    }
}
