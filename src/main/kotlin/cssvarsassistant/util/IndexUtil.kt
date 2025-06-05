package cssvarsassistant.util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project

/** Utility for safe index access that returns an empty list if indices are not ready. */
inline fun <T> safeIndexLookup(project: Project, action: () -> List<T>): List<T> {
    return if (DumbService.isDumb(project)) emptyList() else try {
        action()
    } catch (_: IndexNotReadyException) {
        emptyList()
    }
}