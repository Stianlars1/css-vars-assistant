package cssvarsassistant.documentation.v2


import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import cssvarsassistant.documentation.extractCssVariableName

class CssVariablePsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {

        if (!element.isValid) return null

        val varName = extractCssVariableName(element)

        return varName?.let {
            CssVariableDocumentationTarget(element, it)
        }
    }

}

