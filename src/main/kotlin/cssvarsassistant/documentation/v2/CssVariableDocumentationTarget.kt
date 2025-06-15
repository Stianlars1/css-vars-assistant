package cssvarsassistant.documentation.v2

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

class CssVariableDocumentationTarget(
    private val element: PsiElement,
    private val varName: String
) : DocumentationTarget {


    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(varName)
            .icon(com.intellij.icons.AllIcons.FileTypes.Css)
            .presentation()

    override fun computeDocumentation(): DocumentationResult? {
        println("\n\n\n🔥 V2 API CALLED\n\n\n") // Debug marker
        val html = CssVariableDocumentationService.generateDocumentation(element, varName)
        return html?.let { DocumentationResult.documentation(it) }
    }

    override fun computeDocumentationHint(): String? =
        CssVariableDocumentationService.generateHint(element, varName)

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPointer = SmartPointerManager.getInstance(element.project)
            .createSmartPsiElementPointer(element)

        return Pointer {
            elementPointer.element?.let {
                CssVariableDocumentationTarget(it, varName)
            }
        }
    }
}