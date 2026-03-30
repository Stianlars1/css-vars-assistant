package cssvarsassistant.util

import cssvarsassistant.index.ImportCache
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ScopeUtilTest : CssVarsAssistantPlatformTestCase() {

    fun testProjectOnlyScopeExcludesNodeModulesPreprocessorFiles() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
        }

        val vendorFile = myFixture.addFileToProject(
            "node_modules/vendor/_tokens.scss",
            """
            ${'$'}brand: #ff0000;
            """.trimIndent()
        ).virtualFile

        assertFalse(ScopeUtil.currentPreprocessorScope(project).contains(vendorFile))
    }

    fun testProjectWithImportsScopeOnlyIncludesExplicitlyImportedNodeModulesFiles() {
        updateSettings {
            indexingScope = CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
        }

        val importedVendorFile = myFixture.addFileToProject(
            "node_modules/vendor/_tokens.scss",
            """
            ${'$'}brand: #ff0000;
            """.trimIndent()
        ).virtualFile
        val unrelatedVendorFile = myFixture.addFileToProject(
            "node_modules/vendor/_spacing.scss",
            """
            ${'$'}space: 8px;
            """.trimIndent()
        ).virtualFile

        ImportCache.get(project).add(listOf(importedVendorFile))

        val scope = ScopeUtil.currentPreprocessorScope(project)
        assertTrue(scope.contains(importedVendorFile))
        assertFalse(scope.contains(unrelatedVendorFile))
    }
}
