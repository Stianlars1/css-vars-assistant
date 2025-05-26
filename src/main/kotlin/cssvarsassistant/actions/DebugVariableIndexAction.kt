package cssvarsassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CssVariableIndex
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.settings.CssVarsAssistantSettings

class DebugVariableIndexAction : AnAction("Debug CSS Variable Index") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val settings = CssVarsAssistantSettings.getInstance()
        val scope = when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> GlobalSearchScope.allScope(project)
            else -> GlobalSearchScope.projectScope(project)
        }

        val result = StringBuilder()
        result.append("CSS Variables Index Debug\n")
        result.append("=".repeat(60)).append("\n\n")

        result.append("Settings:\n")
        result.append("  Indexing Scope: ${settings.indexingScope}\n")
        result.append("  Should Resolve Imports: ${settings.shouldResolveImports}\n\n")

        // Get all indexed variable names
        val allVariables = mutableSetOf<String>()
        FileBasedIndex.getInstance().getAllKeys(CssVariableIndex.NAME, project).forEach { varName ->
            allVariables.add(varName)
        }

        result.append("Total indexed variables: ${allVariables.size}\n\n")

        // Group variables by type
        val cssCustomProps = allVariables.filter { it.startsWith("--") }
        val lessVarsConverted = allVariables.filter { it.startsWith("--") &&
                allVariables.none { other -> other == "@" + it.substring(2) } }

        result.append("CSS Custom Properties: ${cssCustomProps.size}\n")
        result.append("LESS Variables (converted): ${lessVarsConverted.size}\n\n")

        // Show sample variables
        result.append("Sample CSS Custom Properties:\n")
        cssCustomProps.take(20).forEach { varName ->
            val values = FileBasedIndex.getInstance()
                .getValues(CssVariableIndex.NAME, varName, scope)
                .flatMap { it.split("|||") }
                .distinct()
                .filter { it.isNotBlank() }

            result.append("  $varName\n")
            values.take(3).forEach { entry ->
                val parts = entry.split(DELIMITER, limit = 3)
                if (parts.size >= 2) {
                    val context = parts[0]
                    val value = parts[1]
                    result.append("    [$context] = $value\n")
                }
            }
        }

        // Search for specific FFE variables
        result.append("\n\nFFE Core Variables Found:\n")
        val ffeVars = allVariables.filter { it.contains("ffe") }
        result.append("  Total FFE variables: ${ffeVars.size}\n")

        // Check for specific variables from theme.less
        val expectedVars = listOf(
            "--ffe-farge-fjell",
            "--ffe-color-background-default",
            "--ffe-color-fill-primary-default",
            "--ffe-fontsize-h1"
        )

        result.append("\nExpected variables check:\n")
        expectedVars.forEach { varName ->
            val found = allVariables.contains(varName)
            result.append("  $varName: ${if (found) "✓ FOUND" else "✗ NOT FOUND"}\n")

            if (found) {
                val values = FileBasedIndex.getInstance()
                    .getValues(CssVariableIndex.NAME, varName, scope)
                    .flatMap { it.split("|||") }
                    .distinct()
                    .filter { it.isNotBlank() }

                values.take(2).forEach { entry ->
                    val parts = entry.split(DELIMITER, limit = 3)
                    if (parts.size >= 2) {
                        result.append("    Value: ${parts[1]}\n")
                    }
                }
            }
        }

        // Show result in editor
        showResultInEditor(project, result.toString(), "css-variables-index-debug.txt")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun showResultInEditor(project: Project, content: String, fileName: String) {
        val tempFile = com.intellij.openapi.util.io.FileUtil.createTempFile(fileName, null)
        tempFile.writeText(content)

        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(tempFile.absolutePath)
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }
}