package cssvarsassistant.index

import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class PreprocessorVariableIndexTest : CssVarsAssistantPlatformTestCase() {

    fun testScssVariablesKeepDollarPrefixAndStripFlags() {
        addProjectStylesheet(
            "tokens.scss",
            """
            ${'$'}brand-primary: #7f80ff !default;
            ${'$'}space-md: 1rem !global;
            """
        )

        assertEquals(listOf("#7f80ff"), readIndexedPreprocessorValues("\$brand-primary"))
        assertEquals(listOf("1rem"), readIndexedPreprocessorValues("\$space-md"))
        assertTrue(readIndexedPreprocessorValues("brand-primary").isEmpty())
    }

    fun testSassIndentedVariablesWithoutSemicolonAreIndexed() {
        addProjectStylesheet(
            "tokens.sass",
            """
            ${'$'}brand-primary: #7f80ff
            ${'$'}space-md: 1rem !default
            """
        )

        assertEquals(listOf("#7f80ff"), readIndexedPreprocessorValues("\$brand-primary"))
        assertEquals(listOf("1rem"), readIndexedPreprocessorValues("\$space-md"))
    }

    fun testLessVariablesKeepAtPrefix() {
        addProjectStylesheet(
            "tokens.less",
            """
            @brand-primary: #7f80ff;
            @space-md: 1rem;
            """
        )

        assertEquals(listOf("#7f80ff"), readIndexedPreprocessorValues("@brand-primary"))
        assertEquals(listOf("1rem"), readIndexedPreprocessorValues("@space-md"))
        assertTrue(readIndexedPreprocessorValues("brand-primary").isEmpty())
    }

    fun testDollarAndAtVariablesWithSameNameStaySeparate() {
        addProjectStylesheet(
            "tokens.scss",
            """
            ${'$'}brand-primary: #111111;
            """
        )
        addProjectStylesheet(
            "tokens.less",
            """
            @brand-primary: #222222;
            """
        )

        assertEquals(listOf("#111111"), readIndexedPreprocessorValues("\$brand-primary"))
        assertEquals(listOf("#222222"), readIndexedPreprocessorValues("@brand-primary"))
    }

    fun testScssComplexMapValueIsKeptRaw() {
        addProjectStylesheet(
            "tokens.scss",
            """
            ${'$'}palette: (
              primary: #ffffff,
              secondary: #000000
            );
            """
        )

        val value = readIndexedPreprocessorValues("\$palette").single()
        assertTrue(value, value.startsWith("("))
        assertTrue(value, value.contains("primary: #ffffff"))
        assertTrue(value, value.contains("secondary: #000000"))
    }

    fun testPreprocessorIndexerIgnoresCommentedDeclarations() {
        addProjectStylesheet(
            "tokens.scss",
            """
            /*
             ${'$'}commented-block: red;
             */
            // ${'$'}commented-line: blue;
            ${'$'}real-token: green;
            """
        )

        assertTrue(readIndexedPreprocessorValues("\$commented-block").isEmpty())
        assertTrue(readIndexedPreprocessorValues("\$commented-line").isEmpty())
        assertEquals(listOf("green"), readIndexedPreprocessorValues("\$real-token"))
    }

    fun testProjectWithImportsIndexesImportedScssPartialVariablesThroughImportingFile() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_tokens.scss",
            """
            ${'$'}vendor-brand: #7f80ff;
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        assertEquals(listOf("#7f80ff"), readIndexedPreprocessorValues("\$vendor-brand"))
    }

    fun testPackageCssFallbackKeepsSassEntrypointVariablesIndexed() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
            maxImportDepth = 5
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_index.scss",
            """
            ${'$'}vendor-brand: var(--brand-primary);
            @mixin core() {
              color: ${'$'}vendor-brand;
            }
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/design/dist/tokens.css",
            """
            :root {
              --brand-primary: #7f80ff;
            }
            """
        )
        addProjectStylesheet(
            "node_modules/@vendor/design/package.json",
            """
            {
              "name": "@vendor/design",
              "sass": "./_index.scss",
              "style": "./dist/tokens.css"
            }
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @use "@vendor/design" as design;
            """
        )

        assertEquals(
            listOf("var(--brand-primary)"),
            readIndexedPreprocessorValues("\$vendor-brand")
        )
        assertContainsElements(
            readIndexedCssEntries("--brand-primary").map { it.value },
            "#7f80ff"
        )
    }

    fun testProjectOnlyDoesNotIndexImportedNodeModulesPreprocessorVariables() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
        }
        addProjectStylesheet(
            "node_modules/@vendor/design/_tokens.scss",
            """
            ${'$'}vendor-brand: #7f80ff;
            """
        )
        addProjectStylesheet(
            "styles/app.scss",
            """
            @import "@vendor/design/tokens";
            """
        )

        assertTrue(readIndexedPreprocessorValues("\$vendor-brand").isEmpty())
    }

    fun testGlobalInputFilterAllowsNodeModulesPreprocessorFiles() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.GLOBAL
        }
        val vendorFile = myFixture.addFileToProject(
            "node_modules/@vendor/design/_tokens.scss",
            """
            ${'$'}vendor-brand: #7f80ff;
            """
        ).virtualFile

        assertTrue(PreprocessorVariableIndex().inputFilter.acceptInput(vendorFile))
    }
}
