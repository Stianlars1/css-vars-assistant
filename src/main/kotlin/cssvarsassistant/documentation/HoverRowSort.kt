package cssvarsassistant.documentation

import cssvarsassistant.util.RankUtil.rank

// Phase 8a (issue #19) follow-up: when selector-shaped contexts entered the
// popup, they ALL landed in `RankUtil.rank()`'s catch-all bucket (tier 9) and
// were sorted alphabetically by lowercase context. In ASCII `.` (0x2E) comes
// before `[` (0x5B), so `.theme-hc` sorted ahead of `[data-theme="dark"]`
// even though the user wrote them in the opposite order in the source file.
//
// This comparator keeps the existing bucket hierarchy (Default → Dark mode →
// media queries by width → …) and adds `(sourceFile, sourceLine)` as the
// secondary tiebreakers, so entries within a bucket render in source order.
// Alphabetical-by-context remains as the final fallback for entries with no
// line info (legacy 3-part index records from 1.8.0 and earlier).
internal fun <T> hoverRowComparator(
    context: (T) -> String,
    sourceFile: (T) -> String?,
    sourceLine: (T) -> Int?
): Comparator<T> = compareBy(
    { rank(context(it)).first },
    { rank(context(it)).second ?: Int.MAX_VALUE },
    { sourceFile(it) ?: "" },
    { sourceLine(it).let { line -> if (line == null || line <= 0) Int.MAX_VALUE else line } },
    { rank(context(it)).third }
)
