// src/main/kotlin/cssvarsassistant/documentation/v2/CssVariablePsiDocumentationTargetProvider.kt
package cssvarsassistant.documentation.v2


import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import cssvarsassistant.documentation.extractCssVariableName

class CssVariablePsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val varName = extractCssVariableName(element) ?: return null
        return CssVariableDocumentationTarget(element, varName)
    }

}

