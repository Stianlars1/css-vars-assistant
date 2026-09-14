package cssvarsassistant.completion

import cssvarsassistant.settings.CssVarsAssistantSettings.SortingOrder
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class CssVariableMixedCatalogCompletionTest : CssVarsAssistantPlatformTestCase() {
    fun testCssMixedCatalogCompletionAndInsertion() = assertMixedCatalog("css", "--")

    fun testScssMixedCatalogCompletionAndInsertion() = assertMixedCatalog("scss", "$")

    fun testSassMixedCatalogCompletionAndInsertion() = assertMixedCatalog("sass", "$")

    fun testLessMixedCatalogCompletionAndInsertion() = assertMixedCatalog("less", "@")

    private fun assertMixedCatalog(extension: String, symbol: String) {
        val tokens = (0 until 40).flatMap { i ->
            listOf(
                "padding-really-super-x-$i" to "4px",
                "pa-color-x-$i" to "#000000",
                "pa-nu-$i" to "0.5"
            )
        }
        val declarations = tokens.joinToString("\n") { (name, value) ->
            "$symbol$name: $value${if (extension == "sass") "" else ";"}"
        }
        addProjectStylesheet(
            "tokens.$extension",
            if (extension == "css") ":root {\n$declarations\n}" else declarations
        )

        for (order in listOf(SortingOrder.ASC, SortingOrder.DESC)) {
            updateSettings { sortingOrder = order }
            val before = when (extension) {
                "css" -> ".card { padding: var(--pa<caret>); }"
                "sass" -> ".card\n  padding: ${symbol}pa<caret>"
                else -> ".card { padding: ${symbol}pa<caret>; }"
            }
            val lookups = completeCssVariablesInProjectFile("app-${order.name}.$extension", before)
            assertEquals(tokens.map { (name, _) -> "$symbol$name" }.toSet(), lookups.map { it.lookupString }.toSet())
            assertEquals(tokens.size, lookups.size)
            for ((name, value) in tokens) {
                assertEquals(value, lookups.single { it.lookupString == "$symbol$name" }.typeText)
            }

            val selected = "${symbol}padding-really-super-x-0"
            val target = myFixture.lookupElements!!.single { it.lookupString == selected }
            myFixture.lookup.currentItem = target
            myFixture.finishLookup('\n')
            myFixture.checkResult(before.replace("${symbol}pa<caret>", selected))
        }
    }
}
