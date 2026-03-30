package cssvarsassistant.index

import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ImportResolverTest : CssVarsAssistantPlatformTestCase() {

    fun testCollectProjectImportsResolvesRelativeAndNodeModulesImports() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val relativeImport = myFixture.addFileToProject(
            "styles/tokens.css",
            """
            :root {
              --local-accent: #111111;
            }
            """.trimIndent()
        ).virtualFile
        val packageImport = myFixture.addFileToProject(
            "node_modules/@vendor/theme.css",
            """
            :root {
              --vendor-accent: #222222;
            }
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "styles/app.css",
            """
            @import "./tokens.css";
            @import "@vendor/theme";
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, relativeImport, packageImport)
    }

    fun testCssIndexImportResolutionDoesNotPopulateImportCache() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        myFixture.addFileToProject(
            "node_modules/vendor/tokens.css",
            """
            :root {
              --accent-1: #111111;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.css",
            """
            @import "vendor/tokens";
            """.trimIndent()
        )

        val entries = readIndexedCssEntries("--accent-1")

        assertContainsElements(entries.map { it.value }, "#111111")
        assertTrue(ImportCache.get(project).get().isEmpty())
    }
}
