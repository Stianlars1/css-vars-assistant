package cssvarsassistant.documentation.v2


import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import cssvarsassistant.documentation.extractCssVariableName

class CssVariablePsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {

        if (!element.isValid) return null

        val source = originalElement?.takeIf { it.isValid } ?: element
        val varName = extractCssVariableName(source) ?: extractCssVariableName(element)

        return varName?.let {
            CssVariableDocumentationTarget(source, it)
        }
    }

}

