package cssvarsassistant.documentation

// 1.8.3 — group hover-popup rows by resolved value so large theme systems
// (shadcn with 5+ flavours, WCAG high-contrast toggles, Catppuccin variants)
// don't show one row per theme when half of them share the same token value.
//
// The signature is generic over the row type so this helper can be unit
// tested against simple dummies without spinning up an IDE fixture. The
// doc service passes its own `EntryWithSource` rows; tests pass records
// that expose only the three fields the collapse actually reads.
//
// Guarantees:
//   - First occurrence of each value keeps its sort position (the iteration
//     order of the input list is preserved via `linkedMapOf`).
//   - Labels within the merged context are de-duplicated and joined with
//     `", "`, so the caller's prettified "Light mode" + "Catppuccin" reads
//     as `Light mode, Catppuccin` in the popup.
//   - Long merged labels are truncated at `maxLabelLength` chars with an
//     ellipsis so 12-theme systems don't blow out the Context column width.
internal fun <T> collapseRowsByValue(
    rows: List<T>,
    value: (T) -> String,
    label: (T) -> String,
    merge: (T, String) -> T,
    maxLabelLength: Int = 80
): List<T> = collapseRowsByValue(
    rows = rows,
    value = value,
    label = label,
    merge = merge,
    combineWithDefault = { false },
    maxLabelLength = maxLabelLength
)

internal fun <T> collapseRowsByValue(
    rows: List<T>,
    value: (T) -> String,
    label: (T) -> String,
    merge: (T, String) -> T,
    combineWithDefault: (T) -> Boolean,
    maxLabelLength: Int = 80
): List<T> {
    if (rows.isEmpty()) return rows

    val rowsByValue = linkedMapOf<String, MutableList<T>>()
    rows.forEach { row ->
        rowsByValue.getOrPut(value(row)) { mutableListOf() }.add(row)
    }

    return rowsByValue.values.map { groupedRows ->
        val first = groupedRows.first()
        val labels = groupedRows.map(label).distinct()
        val defaultCombinableLabels = groupedRows
            .filter(combineWithDefault)
            .map(label)
            .distinct()
            .toSet()
        val canonicalLabels = canonicalizeContextLabels(labels, defaultCombinableLabels)
        val joined = canonicalLabels.joinToString(", ")
        val mergedLabel = if (joined.length <= maxLabelLength) {
            joined
        } else {
            joined.take(maxLabelLength - 1).trimEnd().trimEnd(',') + "…"
        }
        merge(first, mergedLabel)
    }
}

// Issue #29 — after `collapseRowsByValue` groups labels by resolved value,
// pair a lone `Default` label with its first theme sibling so the popup
// shows a single `Default/<Theme>` row instead of `Default, Light`
// (or, worse, `Default, Light, Nova Light, Sepia` — which loses the "these
// themes all match the baseline" story).
//
// Rules:
//   - If the merged group already contains a `Default/<Theme>` label,
//     drop any standalone `Default` — it adds no information.
//   - If the group contains a `Default` label AND at least one label whose
//     source row the caller identified as an explicit theme selector, fuse
//     that pair into `Default/<Theme>`. This semantic predicate prevents
//     unrelated equal-value contexts such as Print or Reduced motion from
//     being mislabeled as themes.
//   - Anything else — no `Default`, or no theme candidate — passes through
//     unchanged so the 1.8.3 "many themes share the same value" merge
//     behaviour is preserved.
private fun canonicalizeContextLabels(
    labels: List<String>,
    defaultCombinableLabels: Set<String>
): List<String> {
    if (labels.isEmpty()) return labels
    val normalized = labels.toMutableList()

    // Drop redundant `Default` when the group already carries a Default/*.
    if (normalized.any { it.startsWith("Default/") }) {
        normalized.remove("Default")
    }

    val defaultIndex = normalized.indexOf("Default")
    if (defaultIndex >= 0) {
        val themeIndex = normalized.indexOfFirst { it in defaultCombinableLabels }
        if (themeIndex >= 0) {
            val theme = normalized[themeIndex]
            val insertIndex = minOf(defaultIndex, themeIndex)
            normalized.removeAt(themeIndex)
            normalized.remove("Default")
            normalized.add(insertIndex.coerceAtMost(normalized.size), "Default/$theme")
        }
    }

    return normalized.distinct()
}
