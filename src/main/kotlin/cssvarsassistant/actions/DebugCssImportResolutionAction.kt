package cssvarsassistant.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import cssvarsassistant.index.ImportResolver
import cssvarsassistant.settings.CssVarsAssistantSettings

/**
 * Debug action to trace CSS import resolution chains
 * Accessible via right-click context menu on CSS/SCSS/SASS/LESS files
 */
class DebugCssImportResolutionAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT // Background thread for better performance
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val settings = CssVarsAssistantSettings.getInstance()

        val debugOutput = ImportResolver.debugImportChain(
            file,
            project,
            settings.maxImportDepth
        )

        val title = "CSS Import Resolution Debug: ${file.name}"
        Messages.showInfoMessage(project, debugOutput, title)
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project

        // Only show for CSS-related files
        val isVisible = project != null && file != null && isCssFile(file)
        e.presentation.isEnabledAndVisible = isVisible

        // Set dynamic text based on file
        if (isVisible) {
            e.presentation.text = "Debug CSS Import Resolution"
            e.presentation.description = "Debug CSS @import resolution chain for ${file!!.name}"
        }
    }

    private fun isCssFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase()
        return extension in setOf("css", "scss", "sass", "less")
    }
}