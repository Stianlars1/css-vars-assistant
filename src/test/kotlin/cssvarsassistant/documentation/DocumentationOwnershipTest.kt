package cssvarsassistant.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.util.PsiTreeUtil
import cssvarsassistant.documentation.v2.CssVariableDocumentationTarget
import cssvarsassistant.documentation.v2.CssVariablePsiDocumentationTargetProvider
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class DocumentationOwnershipTest : CssVarsAssistantPlatformTestCase() {
    fun testVariableNamesStillGetPluginDocumentation() {
        for ((extension, source) in listOf(
            "css" to ":root { --br<caret>and: red; }",
            "css" to ":root { --brand: red; } .a { color: var(--br<caret>and); }",
            "scss" to "\$br<caret>and: red;",
            "scss" to "\$brand: red; .a { color: \$br<caret>and; }",
            "less" to "@br<caret>and: red;",
            "less" to "@brand: red; .a { color: @br<caret>and; }",
            "sass" to "\$brand: red\n.a\n  color: \$br<caret>and",
            "html" to "<style>:root { --brand: red; } .a { color: var(--br<caret>and); }</style>"
        )) {
            myFixture.configureByText("ownership.$extension", source)
            val targets = IdeDocumentationTargetProvider.getInstance(project)
                .documentationTargets(myFixture.editor, myFixture.file, myFixture.caretOffset)
            assertTrue("$source: $targets", targets.any { it is CssVariableDocumentationTarget })
        }
    }

    fun testDeclarationWithoutOriginalElementStillGetsATarget() {
        val file = myFixture.configureByText("app.css", ":root { --brand: red; }")
        val leaf = requireNotNull(file.findElementAt(file.text.indexOf("--brand")))
        val declaration = requireNotNull(PsiTreeUtil.getParentOfType(leaf, CssDeclaration::class.java))
        assertTrue(CssVariablePsiDocumentationTargetProvider().documentationTarget(declaration, null) is CssVariableDocumentationTarget)
    }

    fun testForeignOriginalElementIsNotReinterpretedAsACssDeclaration() {
        val file = myFixture.configureByText("app.css", ":root { --brand: red; }")
        val leaf = requireNotNull(file.findElementAt(file.text.indexOf("--brand")))
        val declaration = requireNotNull(PsiTreeUtil.getParentOfType(leaf, CssDeclaration::class.java))
        val foreign = myFixture.addFileToProject("notes.txt", "--brand")
        assertNull(CssVariablePsiDocumentationTargetProvider().documentationTarget(declaration, foreign.firstChild))
    }
}
