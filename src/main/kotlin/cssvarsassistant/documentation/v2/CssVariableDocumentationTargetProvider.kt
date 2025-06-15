// src/main/kotlin/cssvarsassistant/documentation/v2/CssVariableDocumentationTargetProvider.kt
package cssvarsassistant.documentation.v2

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiFile
import cssvarsassistant.documentation.extractCssVariableName

class CssVariableDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val element = file.findElementAt(offset) ?: return emptyList()
        val varName = extractCssVariableName(element) ?: return emptyList()

        return listOf(CssVariableDocumentationTarget(element, varName))
    }


}