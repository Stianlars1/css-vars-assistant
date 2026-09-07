package cssvarsassistant.documentation

import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.util.PsiTreeUtil
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class VariablePsiContextTest : CssVarsAssistantPlatformTestCase() {
    fun testCssAtRulesAreNotLessVariables() {
        assertName("css", "@me<caret>dia (width > 10px) {}", null)
        assertName("css", "@pro<caret>perty --brand { syntax: '<color>'; }", null)
    }

    fun testScssDirectivesAreNotLessVariables() {
        assertName("scss", "@u<caret>se 'sass:color';", null)
        assertName("scss", "@inc<caret>lude colors;", null)
    }

    fun testCommentsAndStringsDoNotCreateTargets() {
        assertName("less", "/* @br<caret>and */", null)
        assertName("less", "// @br<caret>and", null)
        assertName("scss", ".a { content: '\$br<caret>and'; }", null)
        assertName("css", ".a { content: '--br<caret>and'; }", null)
    }

    fun testCssDeclarationsAndFallbackReferences() {
        assertName("css", ":root { --br<caret>and: red; }", "--brand")
        assertName("css", ".a { color: var(--br<caret>and, red); }", "--brand")
        assertName("css", ".a { color: var(--first, var(--br<caret>and, red)); }", "--brand")
        assertName("css", ".a { color: var(--first, --br<caret>and); }", null)
        assertName("css", ".--br<caret>and { color: red; }", null)
    }

    fun testDirectPreprocessorReferencesAndDeclarations() {
        assertName("scss", ".a { color: \$br<caret>and; }", "\$brand")
        assertName("scss", "\$br<caret>and: red;", "\$brand")
        assertName("sass", ".a\n  color: \$br<caret>and", "\$brand")
        assertName("sass", "\$br<caret>and: red", "\$brand")
        assertName("less", ".a { color: @br<caret>and; }", "@brand")
        assertName("less", "@br<caret>and: red;", "@brand")
    }

    fun testSassModuleQualifierIsPreserved() {
        assertName("scss", ".a { color: theme.\$br<caret>and; }", "theme.\$brand")
    }

    fun testGenuineSassInterpolationRemainsAReference() {
        assertName("scss", ".a { content: '#{\$br<caret>and}'; }", "\$brand")
    }

    fun testForeignJavaNeverCreatesATarget() {
        assertName("java", "class A { String value = \"@br<caret>and\"; }", null)
    }

    fun testEmbeddedCssRemainsAvailable() {
        assertName("html", "<style>.a { color: var(--br<caret>and, red); }</style>", "--brand")
    }

    fun testCompositeDeclarationsUseTheirNameIdentifier() {
        val css = myFixture.configureByText("context.css", ":root { --brand: red; }")
        val cssLeaf = requireNotNull(css.findElementAt(css.text.indexOf("--brand")))
        val declaration = requireNotNull(PsiTreeUtil.getParentOfType(cssLeaf, CssDeclaration::class.java))
        assertEquals("--brand", VariablePsiContext.variableName(declaration))

        for (extension in listOf("scss", "sass", "less")) {
            val name = if (extension == "less") "@brand" else "\$brand"
            val file = myFixture.configureByText("context.$extension", "$name: red;")
            val leaf = requireNotNull(file.findElementAt(2))
            val owner = requireNotNull(PsiTreeUtil.getParentOfType(leaf, PsiNameIdentifierOwner::class.java))
            assertEquals(extension, name, VariablePsiContext.variableName(owner))
        }
    }

    private fun assertName(extension: String, source: String, expected: String?) {
        val file = myFixture.configureByText("context.$extension", source)
        val element = requireNotNull(file.findElementAt(myFixture.caretOffset - 1))
        assertEquals(source, expected, VariablePsiContext.variableName(element))
    }
}
