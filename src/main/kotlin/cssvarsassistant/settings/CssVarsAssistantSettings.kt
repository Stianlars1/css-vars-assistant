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
        /** Small → large (size), low → high (number), hue asc (color). */
        ASC,

        /** Large → small, high → low, hue desc. */
        DESC,

        /**
         * Name-based a-z ordering, ignoring the value entirely. Matches the
         * emergent tiebreaker behaviour that existed before the weigher was
         * introduced — promoted to an explicit option so users who prefer
         * name-sorting get it consistently across both the blank-query
         * autopopup path and fresh invocations.
         */
        ALPHABETICAL
    }

    data class ColumnVisibility(
        var showContext: Boolean = true,
        var showColorSwatch: Boolean = true,
        var showValue: Boolean = true,
        var showType: Boolean = true,
        var showSource: Boolean = true,
        var showPixelEquivalent: Boolean = true,
        var showHexValue: Boolean = true,
        var showWcagContrast: Boolean = true
    )

    data class State(
        var showContextValues: Boolean = true,
        var allowIdeCompletions: Boolean = true,
        var indexingScope: IndexingScope = IndexingScope.PROJECT_WITH_IMPORTS,
        var maxImportDepth: Int = 20,
        var sortingOrder: SortingOrder = SortingOrder.ASC,
        var columnVisibility: ColumnVisibility = ColumnVisibility(),
        // Controls the trailing `— description` that appears in the
        // completion popup next to each variable. Some users find it noisy
        // on narrow popups; disabling it keeps only the colour icon + value.
        var showCompletionDescription: Boolean = true,
        // Maximum characters of the description to render in the popup.
        // 0 = suppress entirely (equivalent to showCompletionDescription=false).
        var completionDescriptionMaxLength: Int = DEFAULT_DESC_MAX_LENGTH,
        // 1.8.2 — when true, the Source column in the hover popup renders
        // as `:220` instead of `variables.css:220`, with the full path shown
        // on hover. IntelliJ's quick-doc popup clamps max-width and wraps
        // long cells; defaulting to compact keeps the popup narrow and
        // non-wrapping in the common case, while power users can turn it
        // off to keep the old verbose display.
        var compactSourceColumn: Boolean = true,
        // 1.8.3 — when true, rows in the hover popup that resolve to the
        // exact same value are merged into a single row whose Context column
        // concatenates every contributing label. Especially useful in design
        // systems with many theme variants (catppuccin, sepia, high-contrast,
        // …) where several themes share the same underlying token value.
        // Defaults on: users who want to audit every declaration can still
        // turn it off to see one row per selector as in 1.8.2.
        var collapseIdenticalValues: Boolean = true
    )

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) {
        val clampedDepth = state.maxImportDepth.coerceIn(1, MAX_IMPORT_DEPTH)
        val clampedDescLen = state.completionDescriptionMaxLength.coerceIn(0, MAX_DESC_MAX_LENGTH)
        this.state = state.copy(
            maxImportDepth = clampedDepth,
            completionDescriptionMaxLength = clampedDescLen
        )
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

    var columnVisibility: ColumnVisibility
        get() = state.columnVisibility
        set(value) {
            state.columnVisibility = value
        }

    var showCompletionDescription: Boolean
        get() = state.showCompletionDescription
        set(value) {
            state.showCompletionDescription = value
        }

    var completionDescriptionMaxLength: Int
        get() = state.completionDescriptionMaxLength
        set(value) {
            state.completionDescriptionMaxLength = value.coerceIn(0, MAX_DESC_MAX_LENGTH)
        }

    var compactSourceColumn: Boolean
        get() = state.compactSourceColumn
        set(value) {
            state.compactSourceColumn = value
        }

    var collapseIdenticalValues: Boolean
        get() = state.collapseIdenticalValues
        set(value) {
            state.collapseIdenticalValues = value
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
        const val DEFAULT_DESC_MAX_LENGTH = 40
        const val MAX_DESC_MAX_LENGTH = 120

        @JvmStatic
        fun getInstance() = com.intellij.openapi.application.ApplicationManager
            .getApplication()
            .getService(CssVarsAssistantSettings::class.java)
    }
}