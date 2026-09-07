package cssvarsassistant.index

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class ImportParsingTest : CssVarsAssistantPlatformTestCase() {
    fun testCanceledImportResolutionPropagates() {
        val entry = myFixture.addFileToProject("app.css", "@import './tokens.css';").virtualFile
        val indicator = EmptyProgressIndicator()
        try {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                ImportResolver.resolveDirectImports(entry, project)
            }, indicator)
            fail("Canceled import resolution must propagate ProcessCanceledException")
        } catch (_: ProcessCanceledException) {
            // Expected cancellation must remain visible to the caller.
        }
    }

    fun testProjectCollectionSkipsExcludedAndUnreferencedPackageSources() {
        myFixture.addFileToProject("node_modules/hidden/tokens.css", ":root { --hidden: red; }")
        myFixture.addFileToProject("node_modules/unused/source.css", "@import 'hidden/tokens.css';")
        val excluded = myFixture.addFileToProject("generated/source.css", "@import 'hidden/tokens.css';").virtualFile.parent
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.first().addExcludeFolder(excluded)
        }
        myFixture.addFileToProject("app.css", ":root { --visible: blue; }")
        assertTrue(ImportResolver.collectProjectImports(project, 5).isEmpty())
    }

    fun testDirectImportsExposeNamespaceAndSuffixInSourceOrder() {
        val source = "/* heading */\n@use './_tokens.scss' as palette with (${'$'}brand: red);\n@forward './base' show brand;\n@import './legacy';"
        val entry = myFixture.addFileToProject("app.scss", source).virtualFile
        val imports = ImportResolver.resolveDirectImports(entry, project)
        assertEquals(listOf("use", "forward", "import"), imports.map { it.kind })
        assertEquals("palette", imports[0].namespace)
        assertEquals("as palette with (${'$'}brand: red)", imports[0].suffix)
        assertEquals(source.indexOf("@use"), imports[0].offset)
        assertEquals("show brand", imports[1].suffix)
    }

    fun testDefaultAndWildcardNamespace() {
        val entry = myFixture.addFileToProject("app.scss", "@use './_tokens.scss'; @use './base' as *;").virtualFile
        assertEquals(listOf("tokens", "*"), ImportResolver.resolveDirectImports(entry, project).map { it.namespace })
    }

    fun testUnsavedDocumentImportsAreUsed() {
        val tokens = myFixture.addFileToProject("tokens.css", ":root { --a: red; }").virtualFile
        val entry = myFixture.addFileToProject("app.css", "").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(entry)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("@import './tokens.css';") }
        assertEquals(tokens, ImportResolver.resolveDirectImports(entry, project).single().resolvedFile)
    }

    fun testDependenciesPrecedeImporter() {
        val base = myFixture.addFileToProject("base.less", "@brand: red;").virtualFile
        val theme = myFixture.addFileToProject("theme.less", "@import './base.less'; @brand: blue;").virtualFile
        val entry = myFixture.addFileToProject("app.less", "@import './theme.less';").virtualFile
        assertEquals(listOf(base, theme), ImportResolver.resolveImports(entry, project, 5).toList())
        assertEquals(listOf(theme), ImportResolver.resolveImports(entry, project, 1).toList())
    }

    fun testPackageExplicitPartialAndDirectoryIndex() {
        val partial = myFixture.addFileToProject("node_modules/design/_tokens.scss", "${'$'}a: red;").virtualFile
        val index = myFixture.addFileToProject("styles/_index.scss", "${'$'}a: red;").virtualFile
        val entry = myFixture.addFileToProject("app.scss", "@use 'design/tokens.scss'; @use './styles';").virtualFile
        assertEquals(listOf(partial, index), ImportResolver.resolveDirectImports(entry, project).map { it.resolvedFile })
    }

    fun testJsonNestedMetadataCannotSupplyEntrypoint() {
        myFixture.addFileToProject("node_modules/design/hidden.scss", "${'$'}a: red;")
        myFixture.addFileToProject("node_modules/design/package.json", """{"metadata":{"sass":"./hidden.scss"}}""")
        val entry = myFixture.addFileToProject("app.scss", "@use 'design';").virtualFile
        assertNull(ImportResolver.resolveDirectImports(entry, project).single().resolvedFile)
    }

    fun testNestedConditionalSubpathExportAndEscapedJsonPath() {
        val tokens = myFixture.addFileToProject("node_modules/design/dist/tokens.scss", "${'$'}a: red;").virtualFile
        myFixture.addFileToProject("node_modules/design/package.json", """{"exports":{"./tokens":{"sass":{"default":".\u002fdist\u002ftokens.scss"}}}}""")
        val entry = myFixture.addFileToProject("app.scss", "@use 'design/tokens';").virtualFile
        assertEquals(tokens, ImportResolver.resolveDirectImports(entry, project).single().resolvedFile)
    }

    fun testDirectPackageResolutionIncludesCssFallback() {
        val sass = myFixture.addFileToProject("node_modules/design/api.scss", "${'$'}a: red;").virtualFile
        val css = myFixture.addFileToProject("node_modules/design/theme.css", ":root { --a: red; }").virtualFile
        myFixture.addFileToProject("node_modules/design/package.json", """{"sass":"./api.scss","style":"./theme.css"}""")
        val entry = myFixture.addFileToProject("app.scss", "@use 'design';").virtualFile
        val resolved = ImportResolver.resolveDirectImports(entry, project).single()
        assertEquals(sass, resolved.resolvedFile)
        assertEquals(listOf(sass, css), resolved.resolvedFiles)
    }

    fun testImportListKeepsQuotedCommaAndLessOptions() {
        val first = myFixture.addFileToProject("a,b.less", "@a: red;").virtualFile
        val second = myFixture.addFileToProject("c.less", "@b: blue;").virtualFile
        val entry = myFixture.addFileToProject("app.less", "@import (reference, less) 'a,b.less', url('./c.less');").virtualFile
        assertEquals(listOf(first, second), ImportResolver.resolveDirectImports(entry, project).map { it.resolvedFile })
    }
}
