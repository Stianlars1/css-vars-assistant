package cssvarsassistant.settings

import com.intellij.openapi.options.Configurable
import com.intellij.util.ui.JBUI
import javax.swing.*
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

class CssVarsAssistantConfigurable : Configurable {
    private val settings = CssVarsAssistantSettings.getInstance()

    // Core display options
    private val showContextValuesCheck = JCheckBox("Show context-based variable values", settings.showContextValues)
    private val allowIdeCompletionsCheck = JCheckBox("Allow IDE built-in completions for variables not found by plugin", settings.allowIdeCompletions)

    // Feature toggles
    private val enableColorPreviewCheck = JCheckBox("Enable color preview in completions", settings.enableColorPreview)
    private val enableHoverDocumentationCheck = JCheckBox("Enable hover documentation", settings.enableHoverDocumentation)
    private val showWebAimLinksCheck = JCheckBox("Show WebAIM contrast checker links", settings.showWebAimLinks)
    private val enableAliasResolutionCheck = JCheckBox("Enable variable alias resolution (var() chains)", settings.enableAliasResolution)
    private val preprocessorVariableSupportCheck = JCheckBox("Support preprocessor variables (LESS/SCSS)", settings.preprocessorVariableSupport)

    // Indexing scope radio buttons
    private val projectOnlyRadio = JRadioButton("Project files only", settings.indexingScope == CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY)
    private val projectWithImportsRadio = JRadioButton("Project files + @import resolution", settings.indexingScope == CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS)
    private val globalRadio = JRadioButton("Full global scope (includes all node_modules)", settings.indexingScope == CssVarsAssistantSettings.IndexingScope.GLOBAL)

    // Performance settings
    private val maxImportDepthSpinner = JSpinner(SpinnerNumberModel(settings.maxImportDepth, 1, 10, 1))
    private val maxCompletionItemsSpinner = JSpinner(SpinnerNumberModel(settings.maxCompletionItems, 10, 200, 10))

    init {
        // Indexing scope button group
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

        var currentRow = 0

        // ===== DISPLAY OPTIONS =====
        gbc.gridy = currentRow++
        panel.add(createSectionLabel("Display Options:"), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(showContextValuesCheck, gbc)

        gbc.gridy = currentRow++
        panel.add(enableColorPreviewCheck, gbc)

        gbc.gridy = currentRow++
        panel.add(enableHoverDocumentationCheck, gbc)

        gbc.gridy = currentRow++
        panel.add(showWebAimLinksCheck, gbc)

        gbc.gridy = currentRow++
        panel.add(allowIdeCompletionsCheck, gbc)

        // ===== FEATURE OPTIONS =====
        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(createSectionLabel("Feature Options:"), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(enableAliasResolutionCheck, gbc)

        gbc.gridy = currentRow++
        panel.add(preprocessorVariableSupportCheck, gbc)

        // Max completion items
        val completionPanel = JPanel()
        completionPanel.add(JLabel("Max completion items:"))
        completionPanel.add(maxCompletionItemsSpinner)
        gbc.gridy = currentRow++
        panel.add(completionPanel, gbc)

        // ===== INDEXING SCOPE =====
        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(createSectionLabel("Variable Indexing Scope:"), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(projectOnlyRadio, gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Only variables defined in your project files are indexed."), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(projectWithImportsRadio, gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Project files + selective resolution of @import statements to external packages."), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Only the exact imported files are indexed, not entire node_modules."), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(5, 15, 2, 5)
        panel.add(globalRadio, gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 25, 2, 5)
        panel.add(createDescriptionLabel("Full indexing of all CSS files in node_modules and libraries."), gbc)

        // Import depth setting
        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        panel.add(createSectionLabel("@import Resolution Depth:"), gbc)

        val depthPanel = JPanel()
        depthPanel.add(JLabel("Maximum @import chain depth:"))
        depthPanel.add(maxImportDepthSpinner)
        depthPanel.add(JLabel("(prevents infinite recursion)"))

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(depthPanel, gbc)

        // Performance warning
        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(20, 5, 5, 5)
        panel.add(createSectionLabel("Performance Note:"), gbc)

        gbc.gridy = currentRow++
        gbc.insets = JBUI.insets(2, 15, 2, 5)
        panel.add(createDescriptionLabel("Global scope indexing may impact IDE performance with large projects."), gbc)

        gbc.gridy = currentRow++
        gbc.weighty = 1.0
        panel.add(Box.createVerticalGlue(), gbc)

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
                enableColorPreviewCheck.isSelected != settings.enableColorPreview ||
                enableHoverDocumentationCheck.isSelected != settings.enableHoverDocumentation ||
                showWebAimLinksCheck.isSelected != settings.showWebAimLinks ||
                enableAliasResolutionCheck.isSelected != settings.enableAliasResolution ||
                preprocessorVariableSupportCheck.isSelected != settings.preprocessorVariableSupport ||
                getSelectedScope() != settings.indexingScope ||
                (maxImportDepthSpinner.value as Int) != settings.maxImportDepth ||
                (maxCompletionItemsSpinner.value as Int) != settings.maxCompletionItems

    override fun apply() {
        settings.showContextValues = showContextValuesCheck.isSelected
        settings.allowIdeCompletions = allowIdeCompletionsCheck.isSelected
        settings.enableColorPreview = enableColorPreviewCheck.isSelected
        settings.enableHoverDocumentation = enableHoverDocumentationCheck.isSelected
        settings.showWebAimLinks = showWebAimLinksCheck.isSelected
        settings.enableAliasResolution = enableAliasResolutionCheck.isSelected
        settings.preprocessorVariableSupport = preprocessorVariableSupportCheck.isSelected
        settings.indexingScope = getSelectedScope()
        settings.maxImportDepth = maxImportDepthSpinner.value as Int
        settings.maxCompletionItems = maxCompletionItemsSpinner.value as Int
    }

    override fun reset() {
        showContextValuesCheck.isSelected = settings.showContextValues
        allowIdeCompletionsCheck.isSelected = settings.allowIdeCompletions
        enableColorPreviewCheck.isSelected = settings.enableColorPreview
        enableHoverDocumentationCheck.isSelected = settings.enableHoverDocumentation
        showWebAimLinksCheck.isSelected = settings.showWebAimLinks
        enableAliasResolutionCheck.isSelected = settings.enableAliasResolution
        preprocessorVariableSupportCheck.isSelected = settings.preprocessorVariableSupport

        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY -> projectOnlyRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> projectWithImportsRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> globalRadio.isSelected = true
        }

        maxImportDepthSpinner.value = settings.maxImportDepth
        maxCompletionItemsSpinner.value = settings.maxCompletionItems
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