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
): List<T> {
    if (rows.isEmpty()) return rows

    val firstByValue = linkedMapOf<String, T>()
    val labelsByValue = linkedMapOf<String, MutableList<String>>()

    rows.forEach { row ->
        val v = value(row)
        if (v !in firstByValue) firstByValue[v] = row
        labelsByValue.getOrPut(v) { mutableListOf() }.add(label(row))
    }

    return firstByValue.map { (v, first) ->
        val mergedLabels = canonicalizeContextLabels(labelsByValue[v]!!.distinct())
        val joined = mergedLabels.joinToString(", ")
        val label = if (joined.length <= maxLabelLength) {
            joined
        } else {
            joined.take(maxLabelLength - 1).trimEnd().trimEnd(',') + "…"
        }
        merge(first, label)
    }
}

private fun canonicalizeContextLabels(labels: List<String>): List<String> {
    if (labels.isEmpty()) return labels
    val normalized = labels.toMutableList()

    // If a merged group already includes a Default/<Theme> label, a standalone
    // Default label adds no information and creates noisy output.
    if (normalized.any { it.startsWith("Default/") }) {
        normalized.remove("Default")
    }

    // When the same resolved value appears for both Default and a theme label,
    // collapse that pair into Default/<Theme> for clearer semantic output.
    val defaultIndex = normalized.indexOf("Default")
    if (defaultIndex >= 0) {
        val themeIndex = normalized.indexOfFirst { label ->
            label != "Default" &&
                !label.startsWith("Default/") &&
                isThemeLabelCandidate(label)
        }
        if (themeIndex >= 0) {
            val theme = normalized[themeIndex]
            normalized.removeAt(themeIndex)
            normalized.remove("Default")
            normalized.add(0, "Default/$theme")
        }
    }

    return normalized.distinct()
}

private fun isThemeLabelCandidate(label: String): Boolean {
    if (label.isBlank()) return false
    if (label.contains(",")) return false
    if (label.contains(":")) return false
    if (label.contains("(") || label.contains(")")) return false
    if (label.contains(" mode", ignoreCase = true)) return false
    return true
}
