package cssvarsassistant.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.atomic.AtomicLong

/** Invalidates queries on saved and unsaved changes without doing work in listeners. */
@Service(Service.Level.PROJECT)
internal class StylesheetChanges(project: Project) : Disposable {
    private val revision = AtomicLong()
    val stamp: Long get() = revision.get()

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) { revision.incrementAndGet() }
        }, this)
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) { revision.incrementAndGet() }
        })
    }

    override fun dispose() = Unit

    companion object {
        fun stamp(project: Project): Long = project.getService(StylesheetChanges::class.java).stamp
    }
}

internal data class StylesheetSnapshot(
    val file: VirtualFile,
    val text: String,
    val css: List<LocatedCssVariable>,
    val preprocessor: PreprocessorFileData,
    val fileStamp: Long,
    val documentStamp: Long?
)

/** Imported/excluded files are read at query time; file-based indexes stay file-local. */
@Service(Service.Level.PROJECT)
internal class StylesheetSnapshots : Disposable {
    private val snapshots = CollectionFactory.createConcurrentWeakKeySoftValueMap<VirtualFile, StylesheetSnapshot>()

    fun get(file: VirtualFile): StylesheetSnapshot? {
        if (!file.isValid || file.isDirectory) return null
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        val fileStamp = file.modificationStamp
        val documentStamp = document?.modificationStamp
        snapshots[file]?.let { if (it.fileStamp == fileStamp && it.documentStamp == documentStamp) return it }
        val text = document?.text ?: VfsUtilCore.loadText(file)
        val extension = file.extension?.lowercase()
        val snapshot = StylesheetSnapshot(
            file, text,
            CssVariableEntryParser.declarations(text, extension),
            PreprocessorVariableEntryParser.declarations(text, extension),
            fileStamp, documentStamp
        )
        snapshots[file] = snapshot
        return snapshot
    }

    override fun dispose() = snapshots.clear()

    companion object {
        fun get(project: Project): StylesheetSnapshots = project.getService(StylesheetSnapshots::class.java)
    }
}
