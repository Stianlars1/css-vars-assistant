package cssvarsassistant.index

import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

/**
 * Issue #28 — modern Sass module syntax (`@use`, `@forward`).
 *
 * Prior to v1.9.2 the import scanner only recognised `@import "..."`.
 * `@use "..."` and `@forward "..."` — the syntax every modern Sass
 * codebase uses — were silently ignored, so token files behind a package
 * root import never entered the index. These tests lock in that both
 * new at-rules resolve their targets, that package-root imports pick up
 * `_index.scss` / `index.scss`, and that the CSS entrypoint fallback
 * kicks in when the Sass entrypoint contains no `--*` declarations.
 */
class SassModuleImportTest : CssVarsAssistantPlatformTestCase() {

    // Bare @use with `as *` (the "everything at package root" pattern from the
    // issue's reproduction). Path resolution must find the underscore-prefixed
    // Sass partial that Sass modules canonically use.
    fun testUseResolvesPackageRootWithSassIndexPartial() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val vendorIndex = myFixture.addFileToProject(
            "node_modules/@vendor/design/_index.scss",
            """
            :root {
              --brand-primary: #7f80ff;
            }
            """.trimIndent()
        ).virtualFile

        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, vendorIndex)
    }

    // @use with a namespaced alias (`as ns`). The alias only affects how
    // consumers reference the imported members from Sass code — from the
    // resolver's perspective only the path matters.
    fun testUseResolvesWithNamespaceAlias() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val tokens = myFixture.addFileToProject(
            "styles/tokens.scss",
            """
            :root {
              --brand-primary: #123456;
            }
            """.trimIndent()
        ).virtualFile

        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "./tokens" as tokens;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, tokens)
    }

    // @forward re-exports another module. Same path resolution rules as @use;
    // the resolver just cares about the path string.
    fun testForwardResolvesRelativeSassPartial() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val foundation = myFixture.addFileToProject(
            "styles/_foundation.scss",
            """
            :root {
              --space-1: 4px;
            }
            """.trimIndent()
        ).virtualFile

        myFixture.addFileToProject(
            "styles/tokens.scss",
            """
            @forward "./foundation";
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "./tokens";
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, foundation)
    }

    // @forward with `hide` / `show` / `as prefix-*` clauses — the resolver
    // still only looks at the quoted path.
    fun testForwardWithHideShowAndPrefixClauses() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val hidden = myFixture.addFileToProject(
            "styles/_hidden.scss",
            """
            :root { --hidden-token: 1; }
            """.trimIndent()
        ).virtualFile
        val prefixed = myFixture.addFileToProject(
            "styles/_prefixed.scss",
            """
            :root { --prefixed-token: 2; }
            """.trimIndent()
        ).virtualFile

        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @forward "./hidden" hide ${'$'}internal;
            @forward "./prefixed" as p-*;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, hidden, prefixed)
    }

    // Existing @import behaviour must not regress alongside the new @use/
    // @forward support. Both syntaxes on the same file should resolve both
    // targets.
    fun testAtImportAndAtUseCoexistOnSameFile() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        val legacy = myFixture.addFileToProject(
            "styles/legacy.css",
            """
            :root { --legacy: red; }
            """.trimIndent()
        ).virtualFile
        val modern = myFixture.addFileToProject(
            "styles/_modern.scss",
            """
            :root { --modern: blue; }
            """.trimIndent()
        ).virtualFile

        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @import "./legacy.css";
            @use "./modern";
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, legacy, modern)
    }

    // Package-entrypoint CSS fallback (also described in #28): when the Sass
    // entrypoint contains no `--*` declarations (a mixin-only "core"), the
    // resolver should also pick up the CSS entrypoint declared in the
    // package's package.json so the tokens still land in the index.
    fun testPackageEntrypointFallbackToCssStyleField() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }

        // Mixin-only Sass entrypoint — no --* declarations.
        val sassEntry = myFixture.addFileToProject(
            "node_modules/@vendor/design/_index.scss",
            """
            @mixin core { color: red; }
            """.trimIndent()
        ).virtualFile
        // CSS entrypoint that DOES carry --* declarations.
        val cssEntry = myFixture.addFileToProject(
            "node_modules/@vendor/design/dist/tokens.css",
            """
            :root { --brand-primary: #abcdef; }
            """.trimIndent()
        ).virtualFile
        // package.json points at the CSS entrypoint through `style`.
        myFixture.addFileToProject(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "sass": "_index.scss",
              "style": "dist/tokens.css"
            }
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "styles/app.scss",
            """
            @use "@vendor/design" as *;
            """.trimIndent()
        )

        val importedFiles = ImportResolver.collectProjectImports(
            project,
            CssVarsAssistantSettings.getInstance().maxImportDepth
        )

        assertContainsElements(importedFiles, sassEntry, cssEntry)
    }
}
