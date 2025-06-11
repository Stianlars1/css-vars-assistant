package cssvarsassistant.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import cssvarsassistant.index.CSS_VARIABLE_INDEXER_NAME
import cssvarsassistant.index.DELIMITER
import cssvarsassistant.documentation.ColorParser
import cssvarsassistant.model.DocParser
import cssvarsassistant.util.PreprocessorUtil
import cssvarsassistant.util.ScopeUtil
import java.util.concurrent.ConcurrentHashMap

data class CompletionEntry(
    val rawName: String,
    val display: String,
    val mainValue: String,
    val allValues: List<Pair<String, String>>,
    val doc: String,
    val isAllColor: Boolean
)

/**
 * PERFORMANCE FIX: Pre-computes and caches all completion data to avoid
 * expensive index queries and file I/O during completion
 */
@Service(Service.Level.PROJECT)
class CompletionDataCache(private val project: Project) {

    private val logger = Logger.getInstance(CompletionDataCache::class.java)
    private val cache = ConcurrentHashMap<String, List<CompletionEntry>>()
    private val ENTRY_SEP = "|||"

    /**
     * PERFORMANCE FIX: Actually use this method instead of recreating the logic
     */
    fun getCompletionEntries(scope: GlobalSearchScope): List<CompletionEntry> {
        // Use stable cache key based on scope characteristics
        val scopeKey = buildScopeKey(scope)

        return cache.computeIfAbsent(scopeKey) {
            logger.info("Computing completion data for scope: $scopeKey")
            precomputeCompletionData(scope)
        }
    }

    private fun buildScopeKey(scope: GlobalSearchScope): String {
        // Create stable cache key that doesn't rely on object identity
        return "${scope.javaClass.simpleName}_${scope.displayName}_${scope.toString().hashCode()}"
    }

    private fun precomputeCompletionData(scope: GlobalSearchScope): List<CompletionEntry> {
        val entries = mutableListOf<CompletionEntry>()
        val keyCache = CssVarKeyCache.get(project)

        try {
            val allKeys = keyCache.getKeys()
            logger.info("Precomputing completion data for ${allKeys.size} variables")

            allKeys.forEach { rawName ->
                ProgressManager.checkCanceled()

                try {
                    val allVals = FileBasedIndex.getInstance()
                        .getValues(CSS_VARIABLE_INDEXER_NAME, rawName, scope)
                        .flatMap { it.split(ENTRY_SEP) }
                        .distinct()
                        .filter { it.isNotBlank() }

                    if (allVals.isNotEmpty()) {
                        val valuePairs = allVals.mapNotNull {
                            val parts = it.split(DELIMITER, limit = 3)
                            if (parts.size >= 2) {
                                val ctx = parts[0]
                                val rawVal = parts[1]
                                // PERFORMANCE FIX: Resolve variables using optimized methods
                                val resolved = resolveVarValue(rawVal, scope)
                                ctx to resolved
                            } else null
                        }

                        val uniqueValuePairs: List<Pair<String, String>> =
                            valuePairs.distinctBy { (ctx, v) -> ctx to v }
                        val values = uniqueValuePairs.map { it.second }.distinct()
                        val mainValue = uniqueValuePairs.find { it.first == "default" }?.second
                            ?: values.firstOrNull() ?: ""

                        val docEntry = allVals.firstOrNull { it.substringAfter(DELIMITER).isNotBlank() }
                            ?: allVals.firstOrNull() ?: ""
                        val commentTxt = docEntry.substringAfter(DELIMITER)
                        val doc = DocParser.parse(commentTxt, mainValue).description

                        val isAllColor = values.isNotEmpty() &&
                                values.all { ColorParser.parseCssColor(it) != null }

                        entries += CompletionEntry(
                            rawName,
                            rawName.removePrefix("--"),
                            mainValue,
                            uniqueValuePairs,
                            doc,
                            isAllColor
                        )
                    }
                } catch (e: Exception) {
                    logger.debug("Error processing variable $rawName", e)
                }
            }

            logger.info("Precomputed ${entries.size} completion entries")
            return entries.sortedBy { it.display }

        } catch (e: Exception) {
            logger.error("Error precomputing completion data", e)
            return emptyList()
        }
    }

    /**
     * Resolve variable values with optimized preprocessor handling
     */
    private fun resolveVarValue(
        raw: String,
        scope: GlobalSearchScope,
        visited: Set<String> = emptySet(),
        depth: Int = 0
    ): String {
        if (depth > 10) return raw // Prevent infinite recursion

        try {
            ProgressManager.checkCanceled()

            // CSS var() references
            val varRef = Regex("""var\(\s*(--[\w-]+)\s*\)""").find(raw)
            if (varRef != null) {
                val ref = varRef.groupValues[1]
                if (ref in visited) return raw

                val refEntries = FileBasedIndex.getInstance()
                    .getValues(CSS_VARIABLE_INDEXER_NAME, ref, scope)
                    .flatMap { it.split(ENTRY_SEP) }
                    .distinct()
                    .filter { it.isNotBlank() }

                val refDefault = refEntries
                    .mapNotNull {
                        val p = it.split(DELIMITER, limit = 3)
                        if (p.size >= 2) p[0] to p[1] else null
                    }
                    .let { pairs ->
                        pairs.find { it.first == "default" }?.second ?: pairs.firstOrNull()?.second
                    }

                if (refDefault != null)
                    return resolveVarValue(refDefault, scope, visited + ref, depth + 1)
                return raw
            }

            // LESS / SCSS preprocessor variables
            val lessMatch = Regex("""^[\s]*[@$]([\w-]+)$""").find(raw.trim())
            if (lessMatch != null) {
                val varName = lessMatch.groupValues[1]

                // Use optimized preprocessor resolution with cached scope
                val preprocessorScope = ScopeUtil.getStablePreprocessorScope(project)
                val resolved = PreprocessorUtil.resolveVariable(project, varName, preprocessorScope)
                return resolved ?: raw
            }

            return raw
        } catch (e: Exception) {
            logger.debug("Error resolving variable value: $raw", e)
            return raw
        }
    }

    fun clear() {
        logger.info("Clearing completion data cache")
        cache.clear()
    }

    companion object {
        @JvmStatic
        fun get(project: Project): CompletionDataCache =
            project.getService(CompletionDataCache::class.java)
    }
}