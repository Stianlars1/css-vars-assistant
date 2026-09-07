package cssvarsassistant.index

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.LightVirtualFile
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class StylesheetSnapshotsTest : CssVarsAssistantPlatformTestCase() {
    fun testUnchangedCatalogReusesSnapshotsBeyond512Files() {
        val cache = StylesheetSnapshots.get(project)
        val files = List(600) { LightVirtualFile("tokens-$it.scss", ":root { --gap: ${it}px; }") }
        val snapshots = files.map { requireNotNull(cache.get(it)) }

        files.zip(snapshots).forEach { (file, snapshot) ->
            assertSame("Unchanged ${file.name} should reuse its parsed snapshot", snapshot, cache.get(file))
        }
    }

    fun testFileStampChangeRebuildsSnapshot() {
        val cache = StylesheetSnapshots.get(project)
        val file = LightVirtualFile("tokens.scss", ":root { --gap: 4px; }")
        val before = requireNotNull(cache.get(file))
        WriteCommandAction.runWriteCommandAction(project) {
            file.setContent(this, ":root { --gap: 8px; }", false)
        }

        val after = requireNotNull(cache.get(file))
        assertNotSame(before, after)
        assertEquals("8px", after.css.single().entry.value)
    }

    fun testUnsavedDocumentStampChangeRebuildsSnapshot() {
        val cache = StylesheetSnapshots.get(project)
        val file = LightVirtualFile("tokens.scss", ":root { --gap: 4px; }")
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
        val before = requireNotNull(cache.get(file))
        val fileStamp = file.modificationStamp
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(":root { --gap: 8px; }")
        }

        val after = requireNotNull(cache.get(file))
        assertEquals(fileStamp, file.modificationStamp)
        assertNotSame(before, after)
        assertEquals("8px", after.css.single().entry.value)
    }

    fun testDisposeClearsCachedSnapshots() {
        val cache = StylesheetSnapshots.get(project)
        val file = LightVirtualFile("tokens.scss", ":root { --gap: 4px; }")
        val before = requireNotNull(cache.get(file))

        cache.dispose()

        assertNotSame(before, cache.get(file))
    }
}
