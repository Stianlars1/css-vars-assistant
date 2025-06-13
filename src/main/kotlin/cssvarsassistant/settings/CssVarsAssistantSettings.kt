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
        PROJECT_ONLY,
        PROJECT_WITH_IMPORTS,
        GLOBAL
    }

    enum class SortingOrder {
        ASC, DESC
    }

    data class State(
        var showContextValues: Boolean = true,
        var allowIdeCompletions: Boolean = true,
        var showUsageBlock: Boolean = true,
        var showDependenciesBlock: Boolean = true,
        var showRelatedBlock: Boolean = true,
        var showFilesBlock: Boolean = true,
        var indexingScope: IndexingScope = IndexingScope.GLOBAL,
        var maxImportDepth: Int = 20,
        var sortingOrder: SortingOrder = SortingOrder.ASC
    )

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) {
        val clamped = state.maxImportDepth.coerceIn(1, MAX_IMPORT_DEPTH)
        this.state = state.copy(maxImportDepth = clamped)
    }

    var showContextValues: Boolean
        get() = state.showContextValues
        set(value) {
            state.showContextValues = value
        }

    var allowIdeCompletions: Boolean
        get() = state.allowIdeCompletions
        set(value) {
            state.allowIdeCompletions = value
        }

    var showUsageBlock: Boolean
        get() = state.showUsageBlock
        set(value) { state.showUsageBlock = value }

    var showDependenciesBlock: Boolean
        get() = state.showDependenciesBlock
        set(value) { state.showDependenciesBlock = value }

    var showRelatedBlock: Boolean
        get() = state.showRelatedBlock
        set(value) { state.showRelatedBlock = value }

    var showFilesBlock: Boolean
        get() = state.showFilesBlock
        set(value) { state.showFilesBlock = value }

    var indexingScope: IndexingScope
        get() = state.indexingScope
        set(value) {
            state.indexingScope = value
        }

    var maxImportDepth: Int
        get() = state.maxImportDepth
        set(value) {
            state.maxImportDepth = value.coerceIn(1, MAX_IMPORT_DEPTH)
        }

    var sortingOrder: SortingOrder
        get() = state.sortingOrder
        set(value) {
            state.sortingOrder = value
        }

    // Computed properties for backward compatibility and clarity
    val useGlobalSearchScope: Boolean
        get() = indexingScope == IndexingScope.GLOBAL

    val shouldResolveImports: Boolean
        get() = indexingScope != IndexingScope.PROJECT_ONLY

    val isProjectScopeOnly: Boolean
        get() = indexingScope == IndexingScope.PROJECT_ONLY

    companion object {
        const val MAX_IMPORT_DEPTH = 20

        @JvmStatic
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication()
            .getService(CssVarsAssistantSettings::class.java)
    }
}