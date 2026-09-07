package cssvarsassistant.audit

import cssvarsassistant.index.ImportResolver
import cssvarsassistant.documentation.findPreprocessorVariableValue
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ImportEdgeCaseAuditTest : CssVarsAssistantPlatformTestCase() {
    fun testCommentedImportsDoNotResolve() {
        addProjectStylesheet("hidden.scss", "${'$'}hidden: red;")
        val entry = myFixture.addFileToProject("app.scss", "/* @use './hidden'; */\n// @import './hidden';").virtualFile
        assertTrue(ImportResolver.resolveImports(entry, project, 5).isEmpty())
    }

    fun testImportsInsideQuotedContentDoNotResolve() {
        addProjectStylesheet("hidden.css", ":root { --hidden: red; }")
        val entry = myFixture.addFileToProject("app.css", ".a { content: \"@import 'hidden.css';\"; }").virtualFile
        assertTrue(ImportResolver.resolveImports(entry, project, 5).isEmpty())
    }

    fun testSassImportListResolvesEveryPath() {
        val first = myFixture.addFileToProject("_first.scss", "${'$'}a: red;").virtualFile
        val second = myFixture.addFileToProject("_second.scss", "${'$'}b: blue;").virtualFile
        val entry = myFixture.addFileToProject("app.scss", "@import 'first', 'second';").virtualFile
        assertEquals(setOf(first, second), ImportResolver.resolveImports(entry, project, 5))
    }

    fun testLessImportOptionsAreRecognized() {
        val tokens = myFixture.addFileToProject("tokens.less", "@brand: red;").virtualFile
        val entry = myFixture.addFileToProject("app.less", "@import (reference) './tokens.less';").virtualFile
        assertEquals(setOf(tokens), ImportResolver.resolveImports(entry, project, 5))
    }

    fun testSassPartialWithExplicitExtensionResolves() {
        val tokens = myFixture.addFileToProject("_tokens.scss", "${'$'}brand: red;").virtualFile
        val entry = myFixture.addFileToProject("app.scss", "@use './tokens.scss';").virtualFile
        assertEquals(setOf(tokens), ImportResolver.resolveImports(entry, project, 5))
    }

    fun testMixedImportAndUseKeepSourceOrder() {
        val first = myFixture.addFileToProject("_first.scss", "${'$'}a: red;").virtualFile
        val second = myFixture.addFileToProject("_second.scss", "${'$'}b: blue;").virtualFile
        val entry = myFixture.addFileToProject("app.scss", "@use './first';\n@import './second';").virtualFile
        assertEquals(listOf(first, second), ImportResolver.resolveDirectImports(entry, project).map { it.resolvedFile })
    }

    fun testTransitiveLessImportKeepsImporterOverride() {
        updateSettings { indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS }
        addProjectStylesheet("node_modules/vendor/base.less", "@brand: red;")
        addProjectStylesheet("node_modules/vendor/theme.less", "@import './base.less';\n@brand: blue;")
        addProjectStylesheet("app.less", "@import 'vendor/theme.less';")
        assertEquals("blue", findPreprocessorVariableValue(project, "@brand")?.resolved)
    }

    fun testMissingImportReturnsEmptyWithoutThrowing() {
        val entry = myFixture.addFileToProject("app.scss", "@use './missing';").virtualFile
        assertTrue(ImportResolver.resolveImports(entry, project, 5).isEmpty())
    }

    fun testImportCycleTerminates() {
        val first = myFixture.addFileToProject("a.css", "@import './b.css';").virtualFile
        val second = myFixture.addFileToProject("b.css", "@import './a.css';").virtualFile
        assertEquals(setOf(first, second), ImportResolver.resolveImports(first, project, 5))
    }
}
