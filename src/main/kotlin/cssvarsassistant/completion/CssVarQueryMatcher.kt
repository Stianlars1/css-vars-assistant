package cssvarsassistant.completion

internal object CssVarQueryMatcher {

    enum class MatchKind(val priority: Int) {
        PREFIX(0),
        TOKEN_PREFIX(1),
        SUBSTRING(2)
    }

    data class Match(
        val kind: MatchKind,
        val matchedPrefix: String
    )

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

    fun withFallback(primary: Query?, prefixMatcherPrefix: String?): Query? {
        if (primary?.normalizedPrefix?.isNotBlank() == true) {
            return primary
        }

        val normalizedPrefix = prefixMatcherPrefix
            ?.trim()
            ?.removePrefix("--")
            ?.takeIf { it.isNotBlank() }
            ?: return primary

        return Query(
            rawPrefix = "--$normalizedPrefix",
            normalizedPrefix = normalizedPrefix
        )
    }

    fun classify(displayName: String, query: Query): MatchKind? {
        return bestMatch(displayName, query)?.kind
    }

    fun bestMatch(displayName: String, query: Query): Match? {
        if (query.normalizedPrefix.isBlank()) return MatchKind.PREFIX
            .let { Match(it, "") }

        val normalizedName = displayName.lowercase()

        for (candidate in candidatePrefixes(query.normalizedPrefix)) {
            val normalizedCandidate = candidate.lowercase()

            when {
                normalizedName.startsWith(normalizedCandidate) ->
                    return Match(MatchKind.PREFIX, candidate)

                normalizedName.split('-').any { it.startsWith(normalizedCandidate) } ->
                    return Match(MatchKind.TOKEN_PREFIX, candidate)

                normalizedName.contains(normalizedCandidate) ->
                    return Match(MatchKind.SUBSTRING, candidate)
            }
        }

        return null
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

    private fun candidatePrefixes(normalizedPrefix: String): List<String> {
        if (normalizedPrefix.isBlank()) {
            return listOf("")
        }

        val parts = normalizedPrefix.split('-').filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return listOf(normalizedPrefix)
        }

        return generateSequence(parts.size) { size ->
            (size - 1).takeIf { it > 0 }
        }.map { size ->
            parts.take(size).joinToString("-")
        }.toList()
    }
}
