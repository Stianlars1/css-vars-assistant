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

    // Regression for issue #18 Bug A: `lastLocalValueInFile` searched the raw
    // file text and picked up `--name: value;` declarations written inside
    // /* ... */ comments, so a changelog-style note in a CSS file would
    // silently override the real cascade winner.
    fun testLastLocalValueInFileIgnoresCommentedSamples() {
        val fileText = """
            :root {
              --primary: #0000ff;
            }

            /*
             * Changelog:
             *   --primary: purple; was used in v1 (deprecated).
             */
        """.trimIndent()

        assertEquals("#0000ff", lastLocalValueInFile(fileText, "--primary"))
    }

    fun testLastLocalValueInFileIgnoresLineComments() {
        val fileText = """
            :root {
              --primary: #0000ff;
            }
            // legacy: --primary: purple;
        """.trimIndent()

        assertEquals("#0000ff", lastLocalValueInFile(fileText, "--primary"))
    }

    fun testLastLocalValueInFileStillFindsRealLocalOverride() {
        // A real local re-declaration (not inside a comment) must still win.
        val fileText = """
            :root {
              --panel-gap: 8px;
            }

            .card {
              --panel-gap: 16px;
            }
        """.trimIndent()

        assertEquals("16px", lastLocalValueInFile(fileText, "--panel-gap"))
    }
}
