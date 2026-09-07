package cssvarsassistant.index

import cssvarsassistant.documentation.VariableResolver
import cssvarsassistant.documentation.CssVariableDocumentationService
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ReleaseReviewRegressionTest : CssVarsAssistantPlatformTestCase() {
    fun testScssUseBeforeFirstDeclarationRemainsUnresolved() {
        val text = ".before { color: ${'$'}brand; }\n${'$'}brand: blue;"
        val file = myFixture.addFileToProject("app.scss", text).virtualFile
        assertEquals("${'$'}brand", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file, text.indexOf("color:") + 8)).resolved)
    }

    fun testLessAliasUsesCallerScopeForLazyEvaluation() {
        val text = ".lazy-eval { width: @var; @a: 9%; }\n@var: @a;\n@a: 100%;"
        val file = myFixture.addFileToProject("app.less", text).virtualFile
        assertEquals("9%", VariableResolver(project).resolve("@var", VariableLocation(file, text.indexOf("width:") + 8)).resolved)
    }

    fun testImportedLessAliasUsesImporterOverride() {
        addProjectStylesheet("library.less", "@base: red;\n@alias: @base;")
        val text = "@import './library.less';\n@base: blue;\n.consumer { color: @alias; }"
        val file = myFixture.addFileToProject("app.less", text).virtualFile
        assertEquals("blue", VariableResolver(project).resolve("@alias", VariableLocation(file, text.lastIndexOf("color:") + 8)).resolved)
    }

    fun testUnrelatedConflictingCssValuesRemainUnresolvedAtUseSite() {
        addProjectStylesheet("red.css", ":root { --base: red; }")
        addProjectStylesheet("blue.css", ":root { --base: blue; }")
        val text = ":root { --brand: var(--base); }"
        val file = myFixture.addFileToProject("app.css", text).virtualFile
        assertEquals("var(--base)", VariableResolver(project).resolve("var(--base)", VariableLocation(file, text.indexOf("--brand"), cssContext = "default")).resolved)
    }

    fun testScssLocalDefaultPreservesVisibleGlobal() {
        val text = "${'$'}brand: red;\n.local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        val file = myFixture.addFileToProject("app.scss", text).virtualFile
        assertEquals("red", VariableResolver(project).resolve("${'$'}brand", VariableLocation(file, text.indexOf("color:") + 8)).resolved)
    }
    fun testCssHintResolvesPreprocessorAliasAtDeclarationPosition() {
        val text = "${'$'}size: 10px;\n:root { --gap: ${'$'}size; }\n${'$'}size: 20px;\n.consumer { padding: var(--gap); }"
        val file = myFixture.addFileToProject("app.scss", text)
        val element = requireNotNull(file.findElementAt(text.lastIndexOf("--gap")))
        val hint = CssVariableDocumentationService.generateHint(element, "--gap")
        assertTrue("Expected original 10px alias value, got: $hint", hint?.endsWith("10px") == true)
    }

    fun testSassUseBeforeFirstDeclarationRemainsUnresolved() {
        val text = ".before\n  color: ${'$'}brand\n${'$'}brand: blue\n"
        assertResolution("app.sass", text, "${'$'}brand", "${'$'}brand")
    }

    fun testScssUseBeforeImportRemainsUnresolved() {
        addProjectStylesheet("_tokens.scss", "${'$'}brand: red;")
        val text = ".before { color: ${'$'}brand; }\n@import './tokens';"
        assertResolution("app.scss", text, "${'$'}brand", "${'$'}brand")
    }

    fun testSassUseBeforeImportRemainsUnresolved() {
        addProjectStylesheet("_tokens.sass", "${'$'}brand: red\n")
        val text = ".before\n  color: ${'$'}brand\n@import './tokens'\n"
        assertResolution("app.sass", text, "${'$'}brand", "${'$'}brand")
    }

    fun testScssUseDoesNotDiscoverImportFromSiblingScope() {
        addProjectStylesheet("_tokens.scss", "${'$'}brand: red;")
        val text = ".library { @import './tokens'; }\n.consumer { color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "${'$'}brand")
    }

    fun testScssUnambiguousProjectDiscoveryIsPreserved() {
        addProjectStylesheet("tokens.scss", "${'$'}brand: red;")
        assertResolution("app.scss", ".consumer { color: ${'$'}brand; }", "${'$'}brand", "red")
    }

    fun testScssOrdinaryLocalAssignmentStillShadowsGlobal() {
        val text = "${'$'}brand: red;\n.local { ${'$'}brand: blue; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testSassLocalDefaultPreservesVisibleGlobal() {
        val text = "${'$'}brand: red\n.local\n  ${'$'}brand: blue !default\n  color: ${'$'}brand\n"
        assertResolution("app.sass", text, "${'$'}brand", "red")
    }

    fun testScssLocalDefaultPreservesVisibleImportedValue() {
        addProjectStylesheet("_tokens.scss", "${'$'}brand: red;")
        val text = "@import './tokens';\n.local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "red")
    }

    fun testScssLocalDefaultPreservesNearestOuterScope() {
        val text = "${'$'}brand: red;\n.outer { ${'$'}brand: green; .inner { ${'$'}brand: blue !default; color: ${'$'}brand; } }"
        assertResolution("app.scss", text, "${'$'}brand", "green")
    }

    fun testScssLocalDefaultReplacesVisibleNull() {
        val text = "${'$'}brand: null;\n.local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testScssLocalDefaultAssignsUndefinedValue() {
        val text = ".local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testScssLocalNullAllowsDefaultDespiteNonNullGlobal() {
        val text = "${'$'}brand: red;\n.local { ${'$'}brand: null; ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testImportedSassAliasKeepsDefinitionBeforeReassignment() {
        addProjectStylesheet("_tokens.scss", "${'$'}base: red; ${'$'}alias: ${'$'}base; ${'$'}base: green;")
        val text = "@use './tokens'; ${'$'}base: blue; .consumer { color: tokens.${'$'}alias; }"
        assertResolution("app.scss", text, "tokens.${'$'}alias", "red")
    }

    fun testSassModuleAliasDoesNotDiscoverImporterVariable() {
        addProjectStylesheet("_tokens.scss", "${'$'}alias: ${'$'}base;")
        val text = "@use './tokens'; ${'$'}base: blue; .consumer { color: tokens.${'$'}alias; }"
        assertResolution("app.scss", text, "tokens.${'$'}alias", "tokens.${'$'}alias")
    }

    fun testImportedLessAliasUsesLibraryValueWithoutOverride() {
        addProjectStylesheet("library.less", "@base: red; @alias: @base;")
        val text = "@import './library.less'; .consumer { color: @alias; }"
        assertResolution("app.less", text, "@alias", "red")
    }

    fun testImportedLessAliasUsesCallerLocalOverride() {
        addProjectStylesheet("library.less", "@base: red; @alias: @base;")
        val text = "@import './library.less'; .consumer { color: @alias; @base: blue; }"
        assertResolution("app.less", text, "@alias", "blue")
    }

    fun testImportedLessAliasCycleRemainsUnresolved() {
        addProjectStylesheet("library.less", "@base: @alias; @alias: @base;")
        val text = "@import './library.less'; .consumer { color: @alias; }"
        assertResolution("app.less", text, "@alias", "@alias")
    }

    fun testCssSameFileValueWinsOverUnrelatedConflicts() {
        addProjectStylesheet("other.css", ":root { --base: red; }")
        assertResolution("app.css", ":root { --base: green; --base: blue; } .consumer { color: var(--base); }", "var(--base)", "blue")
    }

    fun testCssLastImportedValueWinsOverUnrelatedConflicts() {
        addProjectStylesheet("first.css", ":root { --base: red; }")
        addProjectStylesheet("second.css", ":root { --base: blue; }")
        addProjectStylesheet("unrelated.css", ":root { --base: green; }")
        val text = "@import './first.css'; @import './second.css'; .consumer { color: var(--base); }"
        assertResolution("app.css", text, "var(--base)", "blue")
    }

    fun testCssLocalValueWinsOverImportedValue() {
        addProjectStylesheet("tokens.css", ":root { --base: red; }")
        val text = "@import './tokens.css'; :root { --base: blue; } .consumer { color: var(--base); }"
        assertResolution("app.css", text, "var(--base)", "blue")
    }

    fun testCssUniformUnrelatedValuesStillResolve() {
        addProjectStylesheet("first.css", ":root { --base: red; }")
        addProjectStylesheet("second.css", ":root { --base: red; }")
        assertResolution("app.css", ".consumer { color: var(--base); }", "var(--base)", "red")
    }

    fun testImportedCssHintKeepsPreprocessorDeclarationContext() {
        addProjectStylesheet("_tokens.scss", "${'$'}size: 10px; :root { --gap: ${'$'}size; } ${'$'}size: 20px;")
        val text = "@import './tokens'; ${'$'}size: 30px; .consumer { padding: var(--gap); }"
        val file = myFixture.addFileToProject("app.scss", text)
        val element = requireNotNull(file.findElementAt(text.lastIndexOf("--gap")))
        assertEquals("Resolution: ${'$'}size → 10px", CssVariableDocumentationService.generateHint(element, "--gap"))
    }

    fun testCssHintDoesNotChooseUnrelatedConflictingValues() {
        addProjectStylesheet("red.css", ":root { --base: red; }")
        addProjectStylesheet("blue.css", ":root { --base: blue; }")
        val text = ".consumer { color: var(--base); }"
        val file = myFixture.addFileToProject("app.css", text)
        val element = requireNotNull(file.findElementAt(text.lastIndexOf("--base")))
        assertNull(CssVariableDocumentationService.generateHint(element, "--base"))
    }

    fun testLegacySassImportAliasKeepsUnambiguousProjectDiscovery() {
        addProjectStylesheet("_tokens.scss", "${'$'}alias: ${'$'}base;")
        val text = "${'$'}base: red; @import './tokens'; .consumer { color: ${'$'}alias; }"
        assertResolution("app.scss", text, "${'$'}alias", "red")
    }

    fun testSassProjectAliasKeepsUnambiguousProjectDiscovery() {
        addProjectStylesheet("base.scss", "${'$'}base: red;")
        addProjectStylesheet("tokens.scss", "${'$'}alias: ${'$'}base;")
        assertResolution("app.scss", ".consumer { color: ${'$'}alias; }", "${'$'}alias", "red")
    }

    fun testSassWildcardModuleAliasDoesNotDiscoverImporterVariable() {
        addProjectStylesheet("_tokens.scss", "${'$'}alias: ${'$'}base;")
        val text = "@use './tokens' as *; ${'$'}base: blue; .consumer { color: ${'$'}alias; }"
        assertResolution("app.scss", text, "${'$'}alias", "${'$'}alias")
    }

    fun testScssLocalDefaultReplacesOuterAliasToNull() {
        val text = "${'$'}nil: null; ${'$'}brand: ${'$'}nil; .local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testScssLocalDefaultPreservesOuterAliasToNonNullValue() {
        val text = "${'$'}base: red; ${'$'}brand: ${'$'}base; .local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "red")
    }

    fun testScssLocalDefaultReplacesImportedAliasToNull() {
        addProjectStylesheet("_tokens.scss", "${'$'}nil: null; ${'$'}brand: ${'$'}nil;")
        val text = "@import './tokens'; .local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "blue")
    }

    fun testScssLocalDefaultDoesNotTreatAliasCycleAsNull() {
        addProjectStylesheet("first.scss", "${'$'}brand: ${'$'}base;")
        addProjectStylesheet("second.scss", "${'$'}base: ${'$'}brand;")
        val text = "@import './first.scss'; .local { ${'$'}brand: blue !default; color: ${'$'}brand; }"
        assertResolution("app.scss", text, "${'$'}brand", "${'$'}brand")
    }

    private fun assertResolution(path: String, text: String, reference: String, expected: String) {
        val file = myFixture.addFileToProject(path, text).virtualFile
        val location = VariableLocation(file, text.indexOf("color:") + 8, cssContext = "default")
        assertEquals(expected, VariableResolver(project).resolve(reference, location).resolved)
    }
}
