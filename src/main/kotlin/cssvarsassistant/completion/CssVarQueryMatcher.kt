package cssvarsassistant.completion

internal object CssVarQueryMatcher {

    enum class MatchKind(val priority: Int) {
        PREFIX(0),
        TOKEN_PREFIX(1),
        SUBSTRING(2)
    }

    data class Query(
        val rawPrefix: String,
        val normalizedPrefix: String
    )

    private val varQueryRegex = Regex("""var\s*\(\s*([^) ,]*)$""", RegexOption.IGNORE_CASE)

    fun extractQuery(text: String, offset: Int, windowSize: Int = 200): Query? {
        if (offset < 0 || offset > text.length) return null

        val searchStart = maxOf(0, offset - windowSize)
        val beforeCaret = text.substring(searchStart, offset)
        val match = varQueryRegex.findAll(beforeCaret).lastOrNull() ?: return null
        val rawPrefix = match.groupValues.getOrElse(1) { "" }.trim()

        if (rawPrefix.isNotEmpty() && !rawPrefix.startsWith("--")) {
            return null
        }

        return Query(
            rawPrefix = rawPrefix,
            normalizedPrefix = rawPrefix.removePrefix("--")
        )
    }

    fun classify(displayName: String, query: Query): MatchKind? {
        if (query.normalizedPrefix.isBlank()) return MatchKind.PREFIX

        val normalizedName = displayName.lowercase()
        val normalizedQuery = query.normalizedPrefix.lowercase()

        return when {
            normalizedName.startsWith(normalizedQuery) -> MatchKind.PREFIX
            normalizedName.split('-').any { it.startsWith(normalizedQuery) } -> MatchKind.TOKEN_PREFIX
            normalizedName.contains(normalizedQuery) -> MatchKind.SUBSTRING
            else -> null
        }
    }

    fun <T> keepStrongestMatches(
        entries: List<T>,
        query: Query,
        displayName: (T) -> String
    ): List<T> {
        if (query.normalizedPrefix.isBlank()) return entries

        val ranked = entries.mapNotNull { entry ->
            classify(displayName(entry), query)?.let { matchKind -> entry to matchKind }
        }
        val strongestPriority = ranked.minOfOrNull { (_, matchKind) -> matchKind.priority } ?: return emptyList()
        return ranked
            .filter { (_, matchKind) -> matchKind.priority == strongestPriority }
            .map { (entry, _) -> entry }
    }
}
