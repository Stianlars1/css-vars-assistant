package cssvarsassistant.documentation.tooltip

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import cssvarsassistant.util.CSS_VAR_BALLOON_LINK_PREFIX
import java.net.URLDecoder

class CssVarResolutionLinkHandler : DocumentationLinkHandler {

    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (!url.startsWith(CSS_VAR_BALLOON_LINK_PREFIX)) return null

        val steps = url.removePrefix(CSS_VAR_BALLOON_LINK_PREFIX)
            .split("|").map { URLDecoder.decode(it, Charsets.UTF_8) }

        ApplicationManager.getApplication().invokeLater {
            val listStep = object : BaseListPopupStep<String>("Resolution Steps", steps) {
                override fun getTextFor(value: String) = value
            }
            JBPopupFactory.getInstance().createListPopup(listStep, 8).showInFocusCenter()
        }

        return LinkResolveResult.resolvedTarget(target)
    }
}