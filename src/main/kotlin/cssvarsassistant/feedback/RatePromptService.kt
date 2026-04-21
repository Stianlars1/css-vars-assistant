package cssvarsassistant.feedback

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

@Service(Service.Level.APP)
class RatePromptService {

    @Volatile
    private var shownThisSession = false

    fun recordUsage() {
        val props = PropertiesComponent.getInstance()
        val now = System.currentTimeMillis()

        if (readLong(KEY_INSTALL_MS) == 0L) writeLong(KEY_INSTALL_MS, now)

        val last = readLong(KEY_LAST_USAGE_MS)
        if (now - last < USAGE_THROTTLE_MS) return

        writeLong(KEY_LAST_USAGE_MS, now)
        val next = props.getInt(KEY_USAGE_COUNT, 0) + 1
        props.setValue(KEY_USAGE_COUNT, next.toString())

        ApplicationManager.getApplication().invokeLater(::maybePrompt)
    }

    private fun maybePrompt() {
        if (shownThisSession) return
        val props = PropertiesComponent.getInstance()
        if (props.getBoolean(KEY_DISMISSED, false)) return

        val now = System.currentTimeMillis()
        if (now < readLong(KEY_NEXT_PROMPT_MS)) return

        val installMs = readLong(KEY_INSTALL_MS)
        if (installMs == 0L) return
        val daysInstalled = (now - installMs) / MS_PER_DAY
        if (daysInstalled < MIN_DAYS) return
        if (props.getInt(KEY_USAGE_COUNT, 0) < MIN_USES) return

        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
        shownThisSession = true
        showBalloon(project)
    }

    private fun showBalloon(project: Project) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID) ?: return
        val notif = group.createNotification(
            "Enjoying CSS Variables Assistant?",
            "If it's made your CSS workflow smoother, a quick Marketplace review helps other developers find it.",
            NotificationType.INFORMATION,
        )

        notif.addAction(NotificationAction.createSimpleExpiring("Rate on Marketplace") {
            BrowserUtil.browse(MARKETPLACE_REVIEW_URL)
            PropertiesComponent.getInstance().setValue(KEY_DISMISSED, true)
        })
        notif.addAction(NotificationAction.createSimpleExpiring("Try PxPeek") {
            BrowserUtil.browse(SIBLING_URL)
        })
        notif.addAction(NotificationAction.createSimpleExpiring("Remind me later") {
            writeLong(KEY_NEXT_PROMPT_MS, System.currentTimeMillis() + SNOOZE_DAYS * MS_PER_DAY)
        })
        notif.addAction(NotificationAction.createSimpleExpiring("Don't show again") {
            PropertiesComponent.getInstance().setValue(KEY_DISMISSED, true)
        })

        notif.notify(project)
    }

    private fun readLong(key: String): Long =
        PropertiesComponent.getInstance().getValue(key)?.toLongOrNull() ?: 0L

    private fun writeLong(key: String, value: Long) {
        PropertiesComponent.getInstance().setValue(key, value.toString())
    }

    companion object {
        private const val KEY_PREFIX = "cssvarsassistant.rate"
        private const val KEY_INSTALL_MS = "$KEY_PREFIX.installMs"
        private const val KEY_USAGE_COUNT = "$KEY_PREFIX.usageCount"
        private const val KEY_LAST_USAGE_MS = "$KEY_PREFIX.lastUsageMs"
        private const val KEY_NEXT_PROMPT_MS = "$KEY_PREFIX.nextPromptMs"
        private const val KEY_DISMISSED = "$KEY_PREFIX.dismissed"

        private const val MIN_DAYS = 14L
        private const val MIN_USES = 20
        private const val SNOOZE_DAYS = 14L
        private const val USAGE_THROTTLE_MS = 30L * 60L * 1000L
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

        private const val NOTIFICATION_GROUP_ID = "CSS Vars Assistant"
        private const val MARKETPLACE_REVIEW_URL =
            "https://plugins.jetbrains.com/plugin/27392-css-variables-assistant/reviews"
        private const val SIBLING_URL =
            "https://plugins.jetbrains.com/plugin/31286-pxpeek--css-pixel-hints"

        fun getInstance(): RatePromptService =
            ApplicationManager.getApplication().getService(RatePromptService::class.java)
    }
}
