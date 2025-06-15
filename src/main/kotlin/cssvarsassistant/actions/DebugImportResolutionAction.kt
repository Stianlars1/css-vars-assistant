package cssvarsassistant.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import cssvarsassistant.settings.CssVarsAssistantSettings
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

/**
 * Context-menu action: shows a tree view of every @import that a CSS / SCSS /
 * LESS / SASS file pulls in, up to the max depth set in plugin settings.
 */
class DebugImportResolutionAction : AnAction() {

    private val LOG = Logger.getInstance(DebugImportResolutionAction::class.java)
    private val IMPORT_RE =
        Regex("""@import\s+(?:"([^"]+)"|'([^']+)'|\burl\(\s*(?:"([^"]+)"|'([^']+)'|([^)]+))\s*\))""")

    /* ---------------- Action availability ------------------------------- */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val f = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = e.project != null && f != null && isCssLike(f)
    }

    /* ---------------- Main entry-point ---------------------------------- */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val group = NotificationGroupManager.getInstance().getNotificationGroup("CSS Vars Assistant")
        group.createNotification(
            "CSS Import Debug started",
            "Scanning import chain for <b>${file.name}</b>",
            NotificationType.INFORMATION
        ).notify(project)

        ProgressManager.getInstance().run(object :
            Task.Backgroundable(project, "Import-debug: ${file.name}", true) {

            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.text = "Parsing ${file.name}"
                    indicator.fraction = 0.05

                    val cfg = CssVarsAssistantSettings.getInstance()
                    val out = StringBuilder()
                    out.append("=== CSS Import Resolution Debug ===\n")
                        .append("Root file : ${file.path}\n")
                        .append("Max depth : ${cfg.maxImportDepth}\n\n")

                    val stats = mutableMapOf<String, Int>()
                    val visited = hashSetOf<String>()
                    buildTree(file, project, cfg.maxImportDepth, 0, out, indicator, visited, stats)


                    /* ---- summary ------------------------------------------------------- */
                    val grandTotal = stats.values.sum()
                    out.append("\n=== SUMMARY ===\n")
                        .append("Total unique files : ${visited.size}\n")
                        .append("Total CSS variables: $grandTotal\n")
                        .append("Debug finished     : ${java.time.LocalDateTime.now()}\n")

                    indicator.fraction = 1.0

                    SwingUtilities.invokeLater { showDialog(project, file.name, out.toString()) }
                    group.createNotification("CSS Import Debug finished", NotificationType.INFORMATION)
                        .notify(project)

                    LOG.info("Debug-import done for ${file.path}\n$out")

                } catch (ex: Exception) {
                    LOG.error(ex)
                    group.createNotification(
                        "Import debug failed",
                        ex.message ?: "Unknown error",
                        NotificationType.ERROR
                    )
                        .notify(project)
                }
            }
        })
    }

    /* ------------------------------------------------------------------ */
    /*  Recursive tree builder                                            */
    /* ------------------------------------------------------------------ */
    private fun buildTree(
        file: VirtualFile,
        project: com.intellij.openapi.project.Project,
        maxDepth: Int,
        depth: Int,
        out: StringBuilder,
        indicator: ProgressIndicator,
        visited: MutableSet<String>,
        stats: MutableMap<String, Int>
    ) {
        indicator.checkCanceled()
        val indent = "  ".repeat(depth)

        if (!visited.add(file.path)) {
            out.append("$indent↺ ${file.name} (already visited)\n")
            return
        }
        if (depth >= maxDepth) {
            out.append("$indent[depth-limit reached]\n")
            return
        }

        /* ------------- cache variable-count while we're here ------------- */
        stats.computeIfAbsent(file.path) { countVariablesInFile(file) }

        out.append("$indent📄 ${file.name}\n")

        val content = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return
        val imports = IMPORT_RE.findAll(content)
            .mapNotNull { it.groupValues.drop(1).firstOrNull { s -> s.isNotBlank() } }
            .toList()

        imports.forEachIndexed { i, imp ->
            val connector = if (i == imports.lastIndex) "└─" else "├─"
            val resolved = resolveImportPath(file, imp, project)
            out.append("$indent  $connector $imp → ${resolved?.path ?: "❌"}\n")
            resolved?.let {
                buildTree(it, project, maxDepth, depth + 1, out, indicator, visited, stats)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Path resolution helpers (same rules as ImportResolver)            */
    /* ------------------------------------------------------------------ */
    private fun resolveImportPath(
        currentFile: VirtualFile,
        importPath: String,
        project: com.intellij.openapi.project.Project
    ): VirtualFile? = try {
        when {
            importPath.startsWith("./") || importPath.startsWith("../") ->
                resolveRelativePath(currentFile, importPath)

            importPath.startsWith("/") ->
                project.guessProjectDir()?.findFileByRelativePath(importPath.removePrefix("/"))

            importPath.startsWith("@") ->
                resolveNodeModulesPath(currentFile, importPath, project)

            else -> {   // bare import – try same dir first, then node_modules
                resolveRelativePath(currentFile, importPath)
                    ?: resolveNodeModulesPath(currentFile, importPath, project)
            }
        }
    } catch (_: Exception) {
        null
    }

    /* --- relative ------------------------------------------------------ */
    private fun resolveRelativePath(currentFile: VirtualFile, rel: String): VirtualFile? {
        val dir = currentFile.parent ?: return null
        if (rel.contains('.')) {
            return VfsUtil.findRelativeFile(dir, *rel.split('/').toTypedArray())
        }
        val pref = when (currentFile.extension?.lowercase()) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }
        for (ext in pref) {
            val vf = VfsUtil.findRelativeFile(dir, *("$rel.$ext").split('/').toTypedArray())
            if (vf != null && vf.exists()) return vf
        }
        return null
    }

    /* --- node_modules -------------------------------------------------- */
    private fun resolveNodeModulesPath(
        currentFile: VirtualFile,
        pkgPath: String,
        project: com.intellij.openapi.project.Project
    ): VirtualFile? {
        var search = currentFile.parent
        while (search != null) {
            val nm = search.findChild("node_modules")
            if (nm != null) {
                resolveInNodeModules(nm, pkgPath, currentFile)?.let { return it }
            }
            search = search.parent
        }
        project.guessProjectDir()
            ?.findChild("node_modules")
            ?.let { resolveInNodeModules(it, pkgPath, currentFile) }
            ?.let { return it }
        return null
    }

    private fun resolveInNodeModules(
        nodeModules: VirtualFile,
        pkgPath: String,
        importing: VirtualFile
    ): VirtualFile? {
        if (pkgPath.contains('.')) {
            var cur = nodeModules
            for (part in pkgPath.split('/')) {
                cur = cur.findChild(part) ?: return null
            }
            return if (!cur.isDirectory) cur else null
        }

        val pref = when (importing.extension?.lowercase()) {
            "scss" -> listOf("scss", "css", "sass", "less")
            "sass" -> listOf("sass", "scss", "css", "less")
            "less" -> listOf("less", "css", "scss", "sass")
            else -> listOf("css", "scss", "sass", "less")
        }
        for (ext in pref) {
            val parts = "$pkgPath.$ext".split('/')
            var cur = nodeModules
            for (p in parts) {
                cur = cur.findChild(p) ?: break
            }
            if (cur != nodeModules && !cur.isDirectory && cur.exists()) return cur
        }
        return null
    }

    /* ------------------------------------------------------------------ */
    /*  UI helpers                                                        */
    /* ------------------------------------------------------------------ */
    private fun showDialog(project: com.intellij.openapi.project.Project, name: String, text: String) {
        object : DialogWrapper(project) {
            init {
                title = "CSS Import Debug – $name"; init()
            }

            override fun createCenterPanel(): JComponent {
                val ta = JTextArea(text).apply {
                    isEditable = false
                    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    background = UIManager.getColor("Panel.background")
                }
                return JBScrollPane(ta).apply {
                    preferredSize = Dimension(900, 600)
                    border = JBUI.Borders.empty(8)
                }
            }

            override fun createActions(): Array<Action> = arrayOf(okAction)
        }.show()
    }

    private fun isCssLike(f: VirtualFile) =
        f.extension?.lowercase() in setOf("css", "scss", "sass", "less")


    /**
     * Count CSS variables in a file
     */
    private fun countVariablesInFile(file: VirtualFile): Int = try {
        val content = String(file.contentsToByteArray())
        val patterns = listOf(
            Regex("""--[\w-]+\s*:\s*[^;]+;"""),  // css custom prop
            Regex("""@[\w-]+\s*:\s*[^;]+;"""),   // less
            Regex("""\$[\w-]+\s*:\s*[^;]+;""")   // scss / sass
        )
        patterns.sumOf { it.findAll(content).count() }
    } catch (_: Exception) {
        0
    }


}
