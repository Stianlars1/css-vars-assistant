package cssvarsassistant.settings

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.ui.JBUI
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.completion.CssVariableIndexRebuilder
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.ImportCache
import cssvarsassistant.util.PreprocessorUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import javax.swing.*

class CssVarsAssistantConfigurable : Configurable {
    private val settings = CssVarsAssistantSettings.getInstance()

    private val showContextValuesCheck = JCheckBox("Show context-based variable values", settings.showContextValues)
    private val allowIdeCompletionsCheck =
        JCheckBox("Allow IDE built-in completions for variables not found by plugin", settings.allowIdeCompletions)

    // Radio buttons for indexing scope
    private val projectOnlyRadio = JRadioButton(
        "Project files only",
        settings.indexingScope == CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
    )
    private val projectWithImportsRadio = JRadioButton(
        "Project files + @import resolution",
        settings.indexingScope == CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
    )
    private val globalRadio = JRadioButton(
        "Full global scope (includes all node_modules)",
        settings.indexingScope == CssVarsAssistantSettings.IndexingScope.GLOBAL
    )

    private val maxImportDepthSpinner = JSpinner(
        SpinnerNumberModel(
            settings.maxImportDepth.coerceIn(1, CssVarsAssistantSettings.MAX_IMPORT_DEPTH),
            1,
            CssVarsAssistantSettings.MAX_IMPORT_DEPTH,
            1
        )
    )
// Enhanced re-index button implementation
// Replace the existing reindexButton ActionListener in CssVarsAssistantConfigurable.kt

    private val reindexButton = JButton("Re-index variables now…").apply {
        toolTipText = "Flush caches and rebuild the CSS variable index"

        addActionListener {
            isEnabled = false

            val project = ProjectManager.getInstance().openProjects.firstOrNull()
                ?: return@addActionListener.also { isEnabled = true }

            // Show immediate feedback
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CSS Vars Assistant")
                .createNotification(
                    "Starting CSS variable index rebuild...",
                    NotificationType.INFORMATION
                )
                .notify(project)

            ProgressManager.getInstance()
                .run(object : Task.Backgroundable(project, "Rebuilding CSS Variables Index") {
                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = false
                        indicator.fraction = 0.0

                        try {
                            indicator.text = "Clearing import cache..."
                            indicator.fraction = 0.1
                            ImportCache.get(project).clear(project)
                            Thread.sleep(200) // Small delay for visual feedback

                            indicator.text = "Clearing preprocessor cache..."
                            indicator.fraction = 0.3
                            PreprocessorUtil.clearCache()
                            Thread.sleep(200)

                            indicator.text = "Clearing completion cache..."
                            indicator.fraction = 0.5
                            CssVarCompletionCache.clearCaches()
                            Thread.sleep(200)


                            indicator.text = "Requesting index rebuild..."
                            indicator.fraction = 0.7
                            CssVariableIndexRebuilder.forceRebuild()

                            indicator.text = "Finalizing rebuild..."
                            indicator.fraction = 0.9

                            // Wait a bit to see if IntelliJ's indexing kicks in
                            Thread.sleep(1000)
                            indicator.fraction = 1.0

                        } catch (e: Exception) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("CSS Vars Assistant")
                                .createNotification(
                                    "Error during index rebuild: ${e.message}",
                                    NotificationType.ERROR
                                )
                                .notify(project)
                        }
                    }

                    override fun onSuccess() {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("CSS Vars Assistant")
                            .createNotification(
                                "CSS variable index rebuild completed successfully",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }

                    override fun onFinished() {
                        SwingUtilities.invokeLater { isEnabled = true }
                    }
                })

        }
    }

    init {
        val scopeGroup = ButtonGroup()
        scopeGroup.add(projectOnlyRadio)
        scopeGroup.add(projectWithImportsRadio)
        scopeGroup.add(globalRadio)

        // Add listener to enable/disable import depth spinner
        val importDepthListener = ActionListener { updateImportDepthState() }
        projectOnlyRadio.addActionListener(importDepthListener)
        projectWithImportsRadio.addActionListener(importDepthListener)
        globalRadio.addActionListener(importDepthListener)

        updateImportDepthState()
    }

    override fun getDisplayName() = "CSS Variables Assistant"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = JBUI.insets(5)
        gbc.weightx = 1.0

        // Display options
        gbc.gridy = 0
        panel.add(createSectionLabel("Display Options:"), gbc)

        gbc.gridy = 1
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(showContextValuesCheck, gbc)

        gbc.gridy = 2
        panel.add(allowIdeCompletionsCheck, gbc)

        // Indexing scope section
        gbc.gridy = 3
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(createSectionLabel("Variable Indexing Scope:"), gbc)

        gbc.gridy = 4
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(projectOnlyRadio, gbc)

        gbc.gridy = 5
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Only variables defined in your project files are indexed."), gbc)

        gbc.gridy = 6
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(projectWithImportsRadio, gbc)

        gbc.gridy = 7
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(
            createDescriptionLabel("Project files + selective resolution of @import statements to external packages."),
            gbc
        )

        gbc.gridy = 8
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Only the exact imported files are indexed, not entire node_modules."), gbc)

        gbc.gridy = 9
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(globalRadio, gbc)

        gbc.gridy = 10
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Full indexing of all CSS files in node_modules and libraries."), gbc)

        // Import depth setting
        gbc.gridy = 11
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        panel.add(createSectionLabel("@import Resolution Depth:"), gbc)

        val depthPanel = JPanel()
        depthPanel.add(JLabel("Maximum @import chain depth:"))
        depthPanel.add(maxImportDepthSpinner)
        depthPanel.add(JLabel("(prevents infinite recursion)"))

        gbc.gridy = 12
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(depthPanel, gbc)

        // Performance warning
        gbc.gridy = 13
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(createSectionLabel("Performance Note:"), gbc)

        gbc.gridy = 14
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(createDescriptionLabel("Global scope indexing may impact IDE performance with large projects."), gbc)

        gbc.gridy = 15
        gbc.weighty = 1.0
        panel.add(Box.createVerticalGlue(), gbc)

        gbc.gridy = 16
        gbc.insets = JBUI.insets(25, 5, 5, 5)
        panel.add(reindexButton, gbc)

        return panel
    }

    private fun createSectionLabel(text: String): JLabel {
        val label = JLabel(text)
        label.font = label.font.deriveFont(label.font.style or java.awt.Font.BOLD)
        return label
    }

    private fun createDescriptionLabel(text: String): JLabel {
        val label = JLabel(text)
        label.font = label.font.deriveFont(label.font.size - 1f)
        return label
    }

    private fun updateImportDepthState() {
        val enableDepthSpinner = projectWithImportsRadio.isSelected
        maxImportDepthSpinner.isEnabled = enableDepthSpinner
    }

    override fun isModified(): Boolean =
        showContextValuesCheck.isSelected != settings.showContextValues ||
                allowIdeCompletionsCheck.isSelected != settings.allowIdeCompletions ||
                getSelectedScope() != settings.indexingScope ||
                (maxImportDepthSpinner.value as Int) != settings.maxImportDepth

    override fun apply() {
        settings.showContextValues = showContextValuesCheck.isSelected
        settings.allowIdeCompletions = allowIdeCompletionsCheck.isSelected
        settings.indexingScope = getSelectedScope()

        val newScope = getSelectedScope()
        if (settings.indexingScope != newScope) {
            settings.indexingScope = newScope
            val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
            // Clear all caches and cached import files
            ImportCache.get(project).clear(project)
            PreprocessorUtil.clearCache()
            CssVarCompletionCache.clearCaches()
            // Trigger index rebuild of CSS variables
            FileBasedIndex.getInstance().requestRebuild(CSS_VARIABLE_INDEXER_NAME)
        }
        settings.maxImportDepth = maxImportDepthSpinner.value as Int
    }

    override fun reset() {
        showContextValuesCheck.isSelected = settings.showContextValues
        allowIdeCompletionsCheck.isSelected = settings.allowIdeCompletions

        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY -> projectOnlyRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> projectWithImportsRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> globalRadio.isSelected = true
        }

        maxImportDepthSpinner.value = settings.maxImportDepth
        updateImportDepthState()
    }

    private fun getSelectedScope(): CssVarsAssistantSettings.IndexingScope = when {
        projectOnlyRadio.isSelected -> CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
        projectWithImportsRadio.isSelected -> CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
        globalRadio.isSelected -> CssVarsAssistantSettings.IndexingScope.GLOBAL
        else -> CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
    }

    override fun disposeUIResources() {}
}