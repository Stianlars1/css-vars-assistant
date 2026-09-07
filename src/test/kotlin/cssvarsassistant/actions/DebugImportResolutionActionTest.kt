package cssvarsassistant.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import cssvarsassistant.testing.CssVarsAssistantPlatformTestCase

class DebugImportResolutionActionTest : CssVarsAssistantPlatformTestCase() {
    fun testCanceledDebugTaskPropagatesCancellationWithoutReportingAnError() {
        val file = myFixture.addFileToProject("app.css", ":root { --brand: red; }")
        val group = NotificationGroupManager.getInstance().getNotificationGroup("CSS Vars Assistant")
        val task = DebugImportResolutionAction().createTask(project, file.virtualFile, group)
        val indicator = EmptyProgressIndicator()
        indicator.cancel()
        try {
            task.run(indicator)
            fail("Canceled debug task must stop without displaying results or reporting an IDE error")
        } catch (_: ProcessCanceledException) {
            // Expected: the platform, rather than the action's error handler, receives cancellation.
        }
    }
}
