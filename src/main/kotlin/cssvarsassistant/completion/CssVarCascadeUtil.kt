package cssvarsassistant.completion

import cssvarsassistant.documentation.lastLocalValueInFile

internal object CssVarCascadeUtil {

    fun selectMainValue(
        variableName: String,
        activeFileText: String,
        resolvedValues: List<Pair<String, String>>
    ): String {
        return lastLocalValueInFile(activeFileText, variableName)
            ?: resolvedValues.lastOrNull { it.first == "default" }?.second
            ?: resolvedValues.last().second
    }
}
