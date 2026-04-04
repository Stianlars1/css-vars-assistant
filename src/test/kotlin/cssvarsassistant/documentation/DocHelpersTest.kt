package cssvarsassistant.documentation

import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class DocHelpersTest : CssVarsAssistantPlatformTestCase() {

    fun testPreprocessorResolutionCacheDoesNotLeakCallerSteps() {
        addProjectStylesheet(
            "tokens.scss",
            """
            ${'$'}spacing-base: 8px;
            ${'$'}spacing-lg: ${'$'}spacing-base;
            """
        )

        val withPrefix = resolveVarValue(project, "\$spacing-lg", steps = listOf("var(--panel-gap)"))
        assertEquals("8px", withPrefix.resolved)
        assertEquals(listOf("var(--panel-gap)", "@spacing-lg", "@spacing-base"), withPrefix.steps)

        val withoutPrefix = resolveVarValue(project, "\$spacing-lg")
        assertEquals("8px", withoutPrefix.resolved)
        assertEquals(listOf("@spacing-lg", "@spacing-base"), withoutPrefix.steps)
    }
}
