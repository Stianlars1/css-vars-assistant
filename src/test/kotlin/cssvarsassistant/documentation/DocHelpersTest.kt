package cssvarsassistant.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.psi.PsiField
import com.intellij.psi.util.PsiTreeUtil
import cssvarsassistant.documentation.v2.CssVariableDocumentationTarget
import cssvarsassistant.documentation.v2.CssVariablePsiDocumentationTargetProvider
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
        assertEquals(listOf("var(--panel-gap)", "\$spacing-lg", "\$spacing-base"), withPrefix.steps)

        val withoutPrefix = resolveVarValue(project, "\$spacing-lg")
        assertEquals("8px", withoutPrefix.resolved)
        assertEquals(listOf("\$spacing-lg", "\$spacing-base"), withoutPrefix.steps)
    }

    fun testExtractCssVariableNameFindsDirectScssVariableReference() {
        configureProjectFile(
            "app.scss",
            """
            .card {
              padding: ${'$'}spacing-lg<caret>;
            }
            """
        )

        val element = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset - 1))

        assertEquals("\$spacing-lg", extractCssVariableName(element))
    }

    fun testExtractCssVariableNameFindsDirectLessVariableReference() {
        configureProjectFile(
            "app.less",
            """
            .card {
              padding: @spacing-lg<caret>;
            }
            """
        )

        val element = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset - 1))

        assertEquals("@spacing-lg", extractCssVariableName(element))
    }

    fun testProviderLeavesTypeAnnotatedJavaFieldToPlatformDocumentation() {
        assertProviderLeavesJavaFieldToPlatform(
            """
            class Sample {
                /**
                 * Hello, world.
                 */
                private @Nullable String <caret>test = "test";
            }
            """
        )
    }

    fun testProviderLeavesDeclarationAnnotatedJavaFieldToPlatformDocumentation() {
        assertProviderLeavesJavaFieldToPlatform(
            """
            class Sample {
                /**
                 * Hello, world.
                 */
                @Deprecated
                private String <caret>test = "test";
            }
            """
        )
    }

    fun testQuickDocumentationFallsThroughForJavaMethodWithJavadocTags() {
        configureProjectFile(
            "Sample.java",
            """
            class Sample {
                /**
                 * Returns the supplied value.
                 * @param value the value to return
                 * @return the supplied value
                 */
                String <caret>echo(String value) {
                    return value;
                }
            }
            """
        )

        val targets = IdeDocumentationTargetProvider.getInstance(project)
            .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)

        assertTrue(targets.isNotEmpty())
        assertFalse(targets.any { it is CssVariableDocumentationTarget })
    }

    private fun assertProviderLeavesJavaFieldToPlatform(source: String) {
        configureProjectFile("Sample.java", source)
        val leaf = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset))
        val field = requireNotNull(PsiTreeUtil.getParentOfType(leaf, PsiField::class.java))

        assertNull(CssVariablePsiDocumentationTargetProvider().documentationTarget(field, leaf))
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
