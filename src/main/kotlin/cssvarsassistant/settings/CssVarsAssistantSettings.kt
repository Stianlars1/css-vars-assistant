package cssvarsassistant.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "CssVarsAssistantSettings",
    storages = [Storage("cssVarsAssistant.xml")]
)
@Service
class CssVarsAssistantSettings : PersistentStateComponent<CssVarsAssistantSettings.State> {

    enum class IndexingScope {
        PROJECT_ONLY,           // Only project files, no external resolution
        PROJECT_WITH_IMPORTS,   // Project files + selective @import resolution
        GLOBAL                  // Full node_modules indexing
    }

    data class State(
        // Core display options
        var showContextValues: Boolean = true,
        var allowIdeCompletions: Boolean = true,

        // Indexing configuration
        var indexingScope: IndexingScope = IndexingScope.PROJECT_WITH_IMPORTS,
        var maxImportDepth: Int = 3,

        // Feature toggles (actually used)
        var enableColorPreview: Boolean = true,
        var enableHoverDocumentation: Boolean = true,
        var showWebAimLinks: Boolean = true,
        var enableAliasResolution: Boolean = true,
        var preprocessorVariableSupport: Boolean = true,

        // Performance (simple)
        var maxCompletionItems: Int = 50
    )

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) {
        this.state = state
    }

    // Core display options
    var showContextValues: Boolean
        get() = state.showContextValues
        set(value) { state.showContextValues = value }

    var allowIdeCompletions: Boolean
        get() = state.allowIdeCompletions
        set(value) { state.allowIdeCompletions = value }

    // Indexing configuration
    var indexingScope: IndexingScope
        get() = state.indexingScope
        set(value) { state.indexingScope = value }

    var maxImportDepth: Int
        get() = state.maxImportDepth
        set(value) { state.maxImportDepth = value.coerceIn(1, 10) }

    // Feature toggles
    var enableColorPreview: Boolean
        get() = state.enableColorPreview
        set(value) { state.enableColorPreview = value }

    var enableHoverDocumentation: Boolean
        get() = state.enableHoverDocumentation
        set(value) { state.enableHoverDocumentation = value }

    var showWebAimLinks: Boolean
        get() = state.showWebAimLinks
        set(value) { state.showWebAimLinks = value }

    var enableAliasResolution: Boolean
        get() = state.enableAliasResolution
        set(value) { state.enableAliasResolution = value }

    var preprocessorVariableSupport: Boolean
        get() = state.preprocessorVariableSupport
        set(value) { state.preprocessorVariableSupport = value }

    // Performance
    var maxCompletionItems: Int
        get() = state.maxCompletionItems
        set(value) { state.maxCompletionItems = value.coerceIn(10, 200) }

    // Computed properties for backward compatibility
    val useGlobalSearchScope: Boolean
        get() = indexingScope == IndexingScope.GLOBAL

    val shouldResolveImports: Boolean
        get() = indexingScope == IndexingScope.PROJECT_WITH_IMPORTS

    val isProjectScopeOnly: Boolean
        get() = indexingScope == IndexingScope.PROJECT_ONLY

    companion object {
        @JvmStatic
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication()
            .getService(CssVarsAssistantSettings::class.java)
    }
}