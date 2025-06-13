package cssvarsassistant.documentation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DocStatsCache(private val project: Project) {
    private val usage = ConcurrentHashMap<String, Pair<Int, Int>>()
    private val deps = ConcurrentHashMap<String, List<String>>()
    private val related = ConcurrentHashMap<String, List<String>>()
    private val files = ConcurrentHashMap<String, List<String>>()

    fun usage(varName: String, provider: () -> Pair<Int, Int>): Pair<Int, Int> =
        usage.computeIfAbsent(varName) { provider() }

    fun deps(varName: String, provider: () -> List<String>): List<String> =
        deps.computeIfAbsent(varName) { provider() }

    fun related(varName: String, provider: () -> List<String>): List<String> =
        related.computeIfAbsent(varName) { provider() }

    fun files(varName: String, provider: () -> List<String>): List<String> =
        files.computeIfAbsent(varName) { provider() }

    fun clear() {
        usage.clear()
        deps.clear()
        related.clear()
        files.clear()
    }

    companion object {
        @JvmStatic
        fun get(project: Project): DocStatsCache =
            project.getService(DocStatsCache::class.java)
    }
}
