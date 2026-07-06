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

    fun testCollectProjectImportsResolvesScssPartialsFromNodeModules() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val packageImport = myFixture.addFileToProject(
            "node_modules/@vendor/design/_tokens.scss",
            """
            ${'$'}brand-primary: #7f80ff;
            """
        ).virtualFile
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, packageImport)
    }

    fun testCollectProjectImportsResolvesNestedScssPartials() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val tokensImport = myFixture.addFileToProject(
            "node_modules/@vendor/design/_tokens.scss",
            """
            @import "./foundation";
            ${'$'}space-lg: ${'$'}space-base;
            """
        ).virtualFile
        val foundationImport = myFixture.addFileToProject(
            "node_modules/@vendor/design/_foundation.scss",
            """
            ${'$'}space-base: 8px;
            """
        ).virtualFile
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, tokensImport, foundationImport)
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

    fun testCollectProjectImportsResolvesUseFromNodeModules() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val packageImport = myFixture.addFileToProject(
            "node_modules/@vendor/design/_tokens.scss",
            """
            ${'$'}brand-primary: #7f80ff;
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design/tokens" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, packageImport)
    }

    fun testCollectProjectImportsResolvesForwardChain() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val packageTokens = myFixture.addFileToProject(
            "node_modules/@vendor/design/_tokens.scss",
            """
            @forward "./foundation";
            """.trimIndent()
        ).virtualFile
        val packageFoundation = myFixture.addFileToProject(
            "node_modules/@vendor/design/_foundation.scss",
            """
            ${'$'}space-base: 8px;
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design/tokens" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, packageTokens, packageFoundation)
    }

    fun testCollectProjectImportsResolvesPackageJsonEntrypoint() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val packageEntrypoint = myFixture.addFileToProject(
            "node_modules/@vendor/design/src/tokens.scss",
            """
            ${'$'}brand-primary: #7f80ff;
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "sass": "./src/tokens.scss"
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, packageEntrypoint)
    }

    fun testCollectProjectImportsFallsBackToCssEntrypointWhenSassHasNoCustomProperties() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        myFixture.addFileToProject(
            "node_modules/@vendor/design/_index.scss",
            """
            @mixin core() {
              @include reset;
            }
            """.trimIndent()
        )
        val cssEntrypoint = myFixture.addFileToProject(
            "node_modules/@vendor/design/dist/css/core.css",
            """
            :root {
              --brand-primary: #7f80ff;
            }
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "exports": {
                ".": {
                  "sass": "./_index.scss",
                  "css": "./dist/css/core.css",
                  "style": "./dist/css/core.css"
                }
              },
              "style": "./dist/css/core.css",
              "sass": "./_index.scss"
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, cssEntrypoint)
        assertTrue(importedFiles.none { it.path.endsWith("/_index.scss") })
    }

    fun testCollectProjectImportsKeepsSassEntrypointWhenItDefinesCustomProperties() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val sassEntrypoint = myFixture.addFileToProject(
            "node_modules/@vendor/design/_index.scss",
            """
            :root {
              --brand-primary: #7f80ff;
            }
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "node_modules/@vendor/design/dist/css/core.css",
            """
            :root {
              --brand-primary: #222222;
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "exports": {
                ".": {
                  "sass": "./_index.scss",
                  "css": "./dist/css/core.css"
                }
              }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, sassEntrypoint)
        assertTrue(importedFiles.none { it.path.endsWith("/dist/css/core.css") })
    }

    fun testCollectProjectImportsResolvesCssSubpathExport() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val cssEntrypoint = myFixture.addFileToProject(
            "node_modules/@vendor/design/dist/css/core.css",
            """
            :root {
              --brand-primary: #7f80ff;
            }
            """.trimIndent()
        ).virtualFile
        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "exports": {
                "./css": "./dist/css/core.css"
              }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design/css" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertContainsElements(importedFiles, cssEntrypoint)
    }

    fun testCollectProjectImportsSkipsPackageWithoutStylesheetEntrypoint() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "main": "./dist/index.js"
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(project, CssVarsAssistantSettings.getInstance().maxImportDepth)

        assertDoesntContain(importedFiles.map { it.path }, "node_modules/@vendor/design/dist/index.js")
        assertTrue(importedFiles.none { it.path.contains("node_modules/@vendor/design") })
    }
}
