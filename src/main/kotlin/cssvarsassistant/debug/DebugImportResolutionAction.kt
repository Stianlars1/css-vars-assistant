package cssvarsassistant.debug

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import cssvarsassistant.index.ImportResolver
import cssvarsassistant.settings.CssVarsAssistantSettings

class DebugImportResolutionAction : AnAction("Debug CSS Import Resolution") {
    private val LOG = Logger.getInstance(DebugImportResolutionAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        try {
            val settings = CssVarsAssistantSettings.getInstance()

            LOG.info("=== DEBUG: Import Resolution Test ===")
            LOG.info("File: ${virtualFile.path}")
            LOG.info("Extension: ${virtualFile.extension}")
            LOG.info("Settings scope: ${settings.indexingScope}")
            LOG.info("Max depth: ${settings.maxImportDepth}")

            // Read file content to show imports
            val content = String(virtualFile.contentsToByteArray())
            val importPattern =
                Regex("""@import\s+(?:"([^"]+)"|'([^']+)'|\burl\(\s*(?:"([^"]+)"|'([^']+)'|([^)]+))\s*\))""")
            val foundImports = importPattern.findAll(content).map { match ->
                match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "unknown"
            }.toList()

            LOG.info("Found ${foundImports.size} @import statements:")
            foundImports.forEachIndexed { index, import ->
                LOG.info("  [$index] $import")
            }

            val resolvedFiles = ImportResolver.resolveImports(
                virtualFile,
                project,
                settings.maxImportDepth
            )

            val message = buildString {
                appendLine("DEBUG RESULTS FOR: ${virtualFile.name}")
                appendLine("═══════════════════════════════════")
                appendLine("File: ${virtualFile.path}")
                appendLine("Extension: ${virtualFile.extension}")
                appendLine("Settings: ${settings.indexingScope}")
                appendLine("Max Depth: ${settings.maxImportDepth}")
                appendLine()
                appendLine("Found @import statements (${foundImports.size}):")
                foundImports.forEachIndexed { index, import ->
                    appendLine("  ${index + 1}. $import")
                }
                appendLine()
                appendLine("Resolved files (${resolvedFiles.size}):")
                if (resolvedFiles.isEmpty()) {
                    appendLine("  ❌ NO FILES RESOLVED!")
                } else {
                    resolvedFiles.forEachIndexed { index, file ->
                        appendLine("  ✅ ${index + 1}. ${file.path}")
                    }
                }
                appendLine()
                appendLine("Check IDE logs for detailed resolution process")
            }

            LOG.info("=== RESOLUTION COMPLETE ===")
            LOG.info("Resolved ${resolvedFiles.size} files:")
            resolvedFiles.forEach { LOG.info("  -> ${it.path}") }

            Messages.showInfoMessage(project, message, "Import Resolution Debug")

        } catch (e: Exception) {
            LOG.error("Error in debug action", e)
            Messages.showErrorDialog(project, "Error: ${e.message}", "Debug Error")
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isStylesheet = virtualFile?.extension?.lowercase() in listOf("css", "scss", "sass", "less")
        e.presentation.isEnabledAndVisible = isStylesheet
    }
}