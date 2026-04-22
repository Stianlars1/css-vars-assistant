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
        val joined = labelsByValue[v]!!.distinct().joinToString(", ")
        val label = if (joined.length <= maxLabelLength) {
            joined
        } else {
            joined.take(maxLabelLength - 1).trimEnd().trimEnd(',') + "…"
        }
        merge(first, label)
    }
}
