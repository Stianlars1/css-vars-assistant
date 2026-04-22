package cssvarsassistant.completion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ValueUtil
import java.awt.Color

/**
 * Weigher that orders CSS variable lookup items by the user's configured
 * [CssVarsAssistantSettings.SortingOrder].
 *
 * Why a weigher instead of priority on the LookupElement directly:
 * [PrioritizedLookupElement.withPriority] runs as a top-priority weigher in
 * IntelliJ's arranger chain, which freezes the ordering that was computed at
 * element-add time. That was the root cause of Phase 7g: when the autopopup
 * fired at `var(--|)` and contributors ran with a blank query, the comparator's
 * blank-query branch sorted alphabetically, priorities locked that order, and
 * `--foreground` stayed buried while the user typed `--fore`.
 *
 * A [CompletionWeigher] runs AFTER IntelliJ's matchingDegree but BEFORE the
 * insertion-order tiebreaker, so this weigher can ONLY reorder items that our
 * [CssVarQueryMatcher.matchingDegree] already tied (same tier + same name
 * length). That's exactly the group where `--padding-sm` / `--padding-md` /
 * `--padding-lg` end up when a user types `--pad` — the weigher adds the
 * missing signal that says "within a PREFIX tie, use the user's size sort
 * preference".
 *
 * Weights are pre-computed at element-add time and stashed in UserData, so the
 * per-weigh cost is a single map lookup. Items with no stashed sort key return
 * null (no opinion) and the arranger falls back to insertion order.
 */
class CssVarSortOrderWeigher : CompletionWeigher() {

    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<*>? {
        val rank = element.getUserData(SORT_RANK_KEY) ?: return null
        val order = CssVarsAssistantSettings.getInstance().sortingOrder

        // IntelliJ's lookup arranger sorts by weight DESCENDING (higher weight
        // = higher position in the popup).
        //
        //   ASC  → small values first → return -rank (smaller rank becomes
        //          larger weight → ranks higher).
        //   DESC → large values first → return +rank.
        //   ALPHABETICAL → opt out (null). Insertion-order tiebreaker wins,
        //          which is alphabetical because the comparator sorts items
        //          alphabetically when this preference is active.
        return when (order) {
            CssVarsAssistantSettings.SortingOrder.ASC -> -rank
            CssVarsAssistantSettings.SortingOrder.DESC -> rank
            CssVarsAssistantSettings.SortingOrder.ALPHABETICAL -> null
        }
    }

    companion object {
        /**
         * Key for the pre-computed sort rank attached to each lookup element
         * we contribute. Value is a [Long] so the full ordering for sizes,
         * numbers, and colours fits in a single monotonic integer.
         */
        val SORT_RANK_KEY: Key<Long> = Key.create("cssvarsassistant.sort.rank")

        /**
         * Pre-compute the sort rank for a resolved CSS variable value. Returns
         * null for values we can't order meaningfully (e.g. `solid`, keyword
         * values, multi-token shorthand).
         *
         * The returned rank is monotonically increasing so that a plain
         * ascending sort on the raw Long matches the user's expectation:
         *   - Size: pixel count.
         *   - Number: value * 1000 (preserving 3 decimal places of fractional
         *     ordering).
         *   - Colour: hue first, then saturation, then brightness packed into
         *     a single long so HSB ordering survives without a custom
         *     comparator in the weigher.
         *
         * Callers attach the result via `LookupElement.putUserData(SORT_RANK_KEY, rank)`.
         */
        fun computeSortRank(resolvedValue: String): Long? {
            val trimmed = resolvedValue.trim()
            return when (ValueUtil.getValueType(trimmed)) {
                ValueUtil.ValueType.SIZE -> {
                    // Multiply by 1000 to preserve sub-pixel ordering from
                    // `convertToPixels` (which may return fractional values
                    // for units like pt and mm).
                    (ValueUtil.convertToPixels(trimmed) * 1000.0).toLong()
                }

                ValueUtil.ValueType.NUMBER -> {
                    (trimmed.toDoubleOrNull() ?: return null).let { (it * 1000.0).toLong() }
                }

                ValueUtil.ValueType.COLOR -> {
                    val color = cssvarsassistant.documentation.ColorParser.parseCssColor(trimmed)
                        ?: return null
                    val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
                    // Pack hue/sat/bri into a single long. Hue dominates,
                    // saturation breaks hue ties, brightness is the final
                    // tiebreaker — same ordering as ValueUtil.compareColors.
                    // Float values are in [0.0, 1.0]; scale each channel to a
                    // distinct order of magnitude so the lexicographic-by-long
                    // comparison matches HSB-by-field.
                    val hue = (hsb[0] * 1_000_000).toLong()
                    val sat = (hsb[1] * 1_000).toLong()
                    val bri = (hsb[2] * 1_000).toLong()
                    hue * 1_000_000L * 1_000L + sat * 1_000L + bri
                }

                ValueUtil.ValueType.OTHER -> null
            }
        }
    }
}
