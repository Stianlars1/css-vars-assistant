package cssvarsassistant.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.diagnostic.Logger

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

    // IntelliJ's completion framework injects a DUMMY identifier at the caret
    // before running contributors (see `CompletionUtilCore.DUMMY_IDENTIFIER`).
    // In the copy-file text we read via `params.originalFile.text`, that dummy
    // sits past the user's typed prefix. When the caret offset we're passed
    // points AFTER the dummy (as it does in some IDE configurations), the
    // regex capture group swallows the dummy — turning `--err` into
    // `--errIntellijIdeaRulezzz`. Every real variable then fails to match
    // the junk prefix and the popup collapses to "show every variable",
    // which is exactly the bug reported for `hsl(var(--err…))`.
    //
    // Strip any IntelliJ dummy-identifier tail from the captured prefix
    // before we treat it as "what the user typed".
    private const val INTELLIJ_DUMMY = "IntellijIdeaRulezzz"

    fun extractQuery(text: String, offset: Int, windowSize: Int = 200): Query? {
        if (offset < 0 || offset > text.length) return null

        val searchStart = maxOf(0, offset - windowSize)
        // Strip the dummy BEFORE regex matching. The dummy trails with a space
        // (`IntellijIdeaRulezzz `) and the regex capture class `[^) ,]*`
        // excludes spaces, so without this the regex simply fails and we
        // lose the prefix entirely — producing a blank query that makes the
        // lookup popup fall back to "show every variable".
        val beforeCaret = stripCompletionDummy(text.substring(searchStart, offset))
        val match = varQueryRegex.findAll(beforeCaret).lastOrNull() ?: return null
        val rawPrefix = match.groupValues
            .getOrElse(1) { "" }
            // Defense-in-depth in case the dummy happened to be embedded
            // inside the capture group without a preceding non-identifier
            // character.
            .let(::stripCompletionDummy)
            .trim()

        if (rawPrefix.isNotEmpty() && !rawPrefix.startsWith("--")) {
            return null
        }

        return Query(
            rawPrefix = rawPrefix,
            normalizedPrefix = rawPrefix.removePrefix("--")
        )
    }

    private fun stripCompletionDummy(raw: String): String {
        val idx = raw.indexOf(INTELLIJ_DUMMY, ignoreCase = true)
        return if (idx >= 0) raw.substring(0, idx) else raw
    }

    fun withFallback(primary: Query?, prefixMatcherPrefix: String?): Query? {
        if (primary?.normalizedPrefix?.isNotBlank() == true) {
            return primary
        }

        val trimmed = prefixMatcherPrefix
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return primary

        val normalizedPrefix = trimmed.removePrefix("--")

        // Phase 7i: Treat any pure-dash prefix as "no actual query yet". Covers
        //   - trimmed="--" (normalizedPrefix becomes "")
        //   - trimmed="-"  (user just typed the first dash of `--var`; IntelliJ
        //                  autopopup fires here with prefixMatcherPrefix="-")
        //   - trimmed="---" (stray extra dash)
        //
        // Without this, bestMatch("-") treats `-` as a literal search string
        // and drops every variable whose display name has no internal dash —
        // which is exactly how `--error` disappeared from the popup while
        // `--error-foreground` stayed (idea.log line for `raw='-' norm='-'`
        // returned 92 items, all with an internal dash; `--error` absent).
        //
        // rawPrefix MUST reflect what the user actually typed in the document,
        // because IntelliJ uses `PrefixMatcher.prefix.length` to compute the
        // replacement range at insert time. If the user typed `var(error)`
        // without dashes and we synthesise `--error` here, the matcher thinks
        // the typed text is 7 characters long and eats `r(` along with
        // `error` — turning the insert into `hsl(va--error))` (issue #18
        // follow-up "no-dash auto-insert corruption").
        if (normalizedPrefix.isBlank() || normalizedPrefix.all { it == '-' }) {
            return Query(rawPrefix = trimmed, normalizedPrefix = "")
        }

        return Query(
            rawPrefix = trimmed,
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

/**
 * PrefixMatcher that plugs the completion contributor's own [CssVarQueryMatcher.bestMatch]
 * logic into IntelliJ's live lookup filtering.
 *
 * Why this is a named class and not an anonymous object (Issue #18 Phase 7d):
 *   1. `cloneWithPrefix` MUST return a new instance bound to the updated prefix.
 *      Previous versions returned `this`, which froze the matcher's `.prefix`
 *      at whatever the user had typed when the popup first opened — the popup
 *      stopped filtering as the user typed more characters.
 *   2. `prefixMatches` MUST consult `bestMatch`, not just return `true`.
 *      Without this, items like `--ls-normal` keep passing the filter on
 *      any prefix, which is why users saw `ls-normal` in the popup while
 *      typing `--err`.
 *   3. `matchingDegree` and `isStartMatch` MUST be overridden (Phase 7f). The
 *      default [PrefixMatcher.matchingDegree] returns 0 for every item and
 *      the default [PrefixMatcher.isStartMatch] falls back to prefixMatches,
 *      which does not distinguish a prefix match from a token-prefix match
 *      or a substring match. That leaves IntelliJ's lookup arranger with no
 *      signal to rank `--error` ahead of — or even visibly alongside —
 *      `--error-foreground` once the user has typed `--er`. Without an
 *      explicit matchingDegree, the arranger falls back to its own name-
 *      ordering heuristics, which in the real IDE hid `--error` entirely
 *      for some users while the harness (which reads lookupElements directly)
 *      still saw both.
 */
internal class CssVarPrefixMatcher(prefix: String) : PrefixMatcher(prefix) {

    override fun prefixMatches(name: String): Boolean {
        val match = computeMatch(name)
        val result = match != null
        if (LOG.isDebugEnabled) {
            LOG.debug("prefixMatches name='$name' prefix='$prefix' -> $result (${match?.kind})")
        }
        return result
    }

    override fun isStartMatch(name: String): Boolean {
        val match = computeMatch(name) ?: return false
        // Treat PREFIX *and* TOKEN_PREFIX as start matches: both are "the user
        // is clearly building this name from the left". Without this the
        // arranger can demote `--error` below `--error-foreground` for prefix
        // `--er` because IntelliJ falls back to alphabetical name order when
        // no match tier is declared.
        return match.kind == CssVarQueryMatcher.MatchKind.PREFIX ||
            match.kind == CssVarQueryMatcher.MatchKind.TOKEN_PREFIX
    }

    override fun matchingDegree(string: String): Int {
        val match = computeMatch(string) ?: return 0
        // Higher = better; IntelliJ's arranger sorts descending.
        //
        // Three jobs, ordered by weight:
        //  (1) Tier strictly ordered (PREFIX > TOKEN_PREFIX > SUBSTRING) with
        //      enough headroom that the other signals never flip tiers.
        //  (2) Within a tier, items consuming MORE of the user's query
        //      (longer matchedPrefix) rank higher. This is the 1.8.4 fix for
        //      issue #20: when the user types `--sidebar-accent-foreground`
        //      completely, `--sidebar-accent-foreground` matched with the
        //      full 25-char candidate beats `--sidebar` which only matched
        //      a 7-char truncated candidate — even though the latter has a
        //      shorter name. Before, `base - string.length` alone handed
        //      the win to `--sidebar` and pressing Enter overwrote what
        //      the user had typed.
        //  (3) Same-matchedPrefix ties fall back to the 1.8.0 behaviour —
        //      shorter name wins so `--error` stays above `--error-foreground`
        //      for prefix `--er`. Both items' matchedPrefix is `er` there.
        val base = when (match.kind) {
            CssVarQueryMatcher.MatchKind.PREFIX -> 10_000
            CssVarQueryMatcher.MatchKind.TOKEN_PREFIX -> 5_000
            CssVarQueryMatcher.MatchKind.SUBSTRING -> 1_000
        }
        // matchedPrefix.length * 100 dwarfs any realistic name-length delta
        // (variable names don't exceed ~80 chars), so signal (2) dominates
        // signal (3) without risking tier flips.
        return base + match.matchedPrefix.length * 100 - string.length
    }

    override fun cloneWithPrefix(prefix: String): PrefixMatcher = CssVarPrefixMatcher(prefix)

    private fun computeMatch(name: String): CssVarQueryMatcher.Match? {
        val normalized = prefix.removePrefix("--").trim()
        // Phase 7i: any pure-dash prefix (including "-" and "--" alone) is
        // CSS syntax, not a query. Must pass everything so `--error` isn't
        // dropped when IntelliJ filters live with prefix="-".
        if (normalized.isBlank() || normalized.all { it == '-' }) {
            return CssVarQueryMatcher.Match(CssVarQueryMatcher.MatchKind.PREFIX, "")
        }
        val pseudoQuery = CssVarQueryMatcher.Query(prefix, normalized)
        return CssVarQueryMatcher.bestMatch(name.removePrefix("--"), pseudoQuery)
    }

    private companion object {
        private val LOG = Logger.getInstance(CssVarPrefixMatcher::class.java)
    }
}
