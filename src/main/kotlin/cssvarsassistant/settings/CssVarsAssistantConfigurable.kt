package cssvarsassistant.settings

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.ui.JBUI
import cssvarsassistant.completion.CssVarCompletionCache
import cssvarsassistant.index.CssVariableIndexRebuilder
import cssvarsassistant.index.ImportCache
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionListener
import javax.swing.*

/**
 * IDE-innstillinger for CSS Vars Assistant.
 *
 * Implementerer bare [Configurable] og [Disposable] – ikke
 * `WithEpDependencies`, da denne panelet ikke avhenger av egne
 * (dynamiske) extension-points.
 */
class CssVarsAssistantConfigurable : Configurable, Disposable {

    /* ---------- Lifecycle ------------------------------------------------ */

    override fun dispose() {
        /* Ingen egne ressurser å rydde opp i. */
    }

    /* ---------- Model ---------------------------------------------------- */

    private val settings = CssVarsAssistantSettings.getInstance()

    /* ---------- UI controls --------------------------------------------- */

    private val showContextValuesCheck =
        JCheckBox("Show context-based variable values", settings.showContextValues)

    private val allowIdeCompletionsCheck =
        JCheckBox(
            "Allow IDE built-in completions for variables not found by plugin",
            settings.allowIdeCompletions
        )

    // Index-scope
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

    // Sorting order
    private val ascRadio = JRadioButton(
        "Ascending (8px, 16px, 24px)",
        settings.sortingOrder == CssVarsAssistantSettings.SortingOrder.ASC
    )
    private val descRadio = JRadioButton(
        "Descending (24px, 16px, 8px)",
        settings.sortingOrder == CssVarsAssistantSettings.SortingOrder.DESC
    )

    // Import-depth
    private val maxImportDepthSpinner = JSpinner(
        SpinnerNumberModel(
            settings.maxImportDepth.coerceIn(1, CssVarsAssistantSettings.MAX_IMPORT_DEPTH),
            1,
            CssVarsAssistantSettings.MAX_IMPORT_DEPTH,
            1
        )
    )

    // Re-index button
    private val reindexButton = JButton("Re-index variables now…").apply {
        toolTipText = "Flush caches and rebuild the CSS variable index"

        addActionListener {
            isEnabled = false
            val project = ProjectManager.getInstance().openProjects.firstOrNull()
                ?: return@addActionListener.also { isEnabled = true }

            NotificationGroupManager.getInstance()
                .getNotificationGroup("CSS Vars Assistant")
                .createNotification(
                    "Starting CSS variable index rebuild…",
                    NotificationType.INFORMATION
                )
                .notify(project)

            ProgressManager.getInstance()
                .run(object : Task.Backgroundable(project, "Rebuilding CSS Variables Index") {

                    override fun run(indicator: ProgressIndicator) {
                        indicator.isIndeterminate = false

                        fun step(text: String, fraction: Double, action: () -> Unit) {
                            indicator.text = text
                            indicator.fraction = fraction
                            action()
                        }

                        step("Clearing import cache…", 0.10) {
                            ImportCache.get(project).clear()      // ← ingen argument
                        }
                        step("Clearing pre-processor cache…", 0.30) {
                            PreprocessorUtil.clearCache()
                            ScopeUtil.clearAll()
                        }
                        step("Clearing completion cache…", 0.50) {
                            CssVarCompletionCache.clearCaches()
                        }
                        step("Requesting index rebuild…", 0.70) {
                            CssVariableIndexRebuilder.forceRebuild()
                        }
                        indicator.text = "Finalizing…"
                        indicator.fraction = 0.90
                        Thread.sleep(800)   // Liten ventetid for fremdriften
                        indicator.fraction = 1.0
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
        // Radioknappe-grupper
        ButtonGroup().apply {
            add(projectOnlyRadio)
            add(projectWithImportsRadio)
            add(globalRadio)
        }
        ButtonGroup().apply {
            add(ascRadio)
            add(descRadio)
        }

        // Enable/disable depth-spinner
        val l = ActionListener { updateImportDepthState() }
        projectOnlyRadio.addActionListener(l)
        projectWithImportsRadio.addActionListener(l)
        globalRadio.addActionListener(l)

        updateImportDepthState()
    }

    /* ---------- Configurable impl ---------------------------------------- */

    override fun getDisplayName() = "CSS Variables Assistant"

    override fun createComponent(): JComponent = buildPanel()

    override fun disposeUIResources() {}   // nothing extra

    override fun isModified(): Boolean =
        showContextValuesCheck.isSelected != settings.showContextValues ||
                allowIdeCompletionsCheck.isSelected != settings.allowIdeCompletions ||
                getSelectedScope() != settings.indexingScope ||
                (maxImportDepthSpinner.value as Int) != settings.maxImportDepth ||
                getSelectedSortingOrder() != settings.sortingOrder

    override fun apply() {
        settings.showContextValues = showContextValuesCheck.isSelected
        settings.allowIdeCompletions = allowIdeCompletionsCheck.isSelected
        settings.indexingScope = getSelectedScope()
        settings.maxImportDepth = maxImportDepthSpinner.value as Int
        settings.sortingOrder = getSelectedSortingOrder()
    }

    override fun reset() {
        showContextValuesCheck.isSelected = settings.showContextValues
        allowIdeCompletionsCheck.isSelected = settings.allowIdeCompletions
        when (settings.indexingScope) {
            CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY -> projectOnlyRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS -> projectWithImportsRadio.isSelected = true
            CssVarsAssistantSettings.IndexingScope.GLOBAL -> globalRadio.isSelected = true
        }
        when (settings.sortingOrder) {
            CssVarsAssistantSettings.SortingOrder.ASC -> ascRadio.isSelected = true
            CssVarsAssistantSettings.SortingOrder.DESC -> descRadio.isSelected = true
        }
        maxImportDepthSpinner.value = settings.maxImportDepth
        updateImportDepthState()
    }

    /* ---------- Helpers -------------------------------------------------- */

    private fun buildPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5)
            weightx = 1.0
        }

        var row = 0

        fun section(title: String) {
            gbc.gridy = row++
            gbc.insets = JBUI.insets(20, 5, 5, 5)
            panel.add(createSectionLabel(title), gbc)
        }

        fun item(comp: JComponent, indent: Int = 15, descr: String? = null) {
            gbc.gridy = row++
            gbc.insets = JBUI.insets(5, indent, 2, 5)
            panel.add(comp, gbc)
            descr?.let {
                gbc.gridy = row++
                gbc.insets = JBUI.insets(2, indent + 10, 2, 5)
                panel.add(createDescriptionLabel(it), gbc)
            }
        }

        section("Display Options:")
        item(showContextValuesCheck)
        item(allowIdeCompletionsCheck)

        section("Variable Indexing Scope:")
        item(projectOnlyRadio, descr = "Only variables defined in your project files are indexed.")
        item(
            projectWithImportsRadio, descr =
                "Project files plus selective resolution of @import statements to external packages."
        )
        item(createDescriptionLabel("Only the exact imported files are indexed, not entire node_modules."), 25)
        item(globalRadio, descr = "Full indexing of all CSS files in node_modules and libraries.")

        section("@import Resolution Depth:")
        item(JPanel().apply {
            add(JLabel("Maximum @import chain depth:"))
            add(maxImportDepthSpinner)
            add(JLabel("(prevents infinite recursion)"))
        })

        section("Value-Based Sorting:")
        item(ascRadio)
        item(descRadio)

        section("Performance Note:")
        item(createDescriptionLabel("Global scope indexing may impact IDE performance with large projects."))

        // Glue + button
        gbc.gridy = row++
        gbc.weighty = 1.0
        panel.add(Box.createVerticalGlue(), gbc)

        gbc.gridy = row
        gbc.insets = JBUI.insets(25, 5, 5, 5)
        panel.add(reindexButton, gbc)

        return panel
    }

    private fun createSectionLabel(text: String) =
        JLabel(text).apply { font = font.deriveFont(font.style or java.awt.Font.BOLD) }

    private fun createDescriptionLabel(text: String) =
        JLabel(text).apply { font = font.deriveFont(font.size - 1f) }

    private fun updateImportDepthState() {
        maxImportDepthSpinner.isEnabled = projectWithImportsRadio.isSelected
    }

    private fun getSelectedScope(): CssVarsAssistantSettings.IndexingScope = when {
        projectOnlyRadio.isSelected -> CssVarsAssistantSettings.IndexingScope.PROJECT_ONLY
        projectWithImportsRadio.isSelected -> CssVarsAssistantSettings.IndexingScope.PROJECT_WITH_IMPORTS
        else -> CssVarsAssistantSettings.IndexingScope.GLOBAL
    }

    private fun getSelectedSortingOrder(): CssVarsAssistantSettings.SortingOrder = when {
        ascRadio.isSelected -> CssVarsAssistantSettings.SortingOrder.ASC
        else -> CssVarsAssistantSettings.SortingOrder.DESC
    }
}
