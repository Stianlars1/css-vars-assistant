package cssvarsassistant.util

object RankUtil {

    fun rank(ctx: String): Triple<Int, Int?, String> {
        val c = ctx.lowercase().trim()

        // 1. Default and light mode (highest priority)
        if (c == "default" || c == "default/light" || c.isEmpty() ||
            (c.contains("prefers-color-scheme") && c.contains("light"))
        ) {
            return Triple(0, null, c)
        }

        // 2. Dark mode
        if (c.contains("prefers-color-scheme") && c.contains("dark")) {
            return Triple(1, null, c)
        }

        // 3. Min-width (desktop-first: larger screens first)
        extractMediaValue(c, "min-width")?.let { value ->
            return Triple(2, -value, c) // Negative for descending sort
        }

        // 4. Max-width (mobile-first: larger screens first)
        extractMediaValue(c, "max-width")?.let { value ->
            return Triple(3, -value, c) // Negative for descending sort
        }

        // 5. Height-based queries
        extractMediaValue(c, "min-height")?.let { value ->
            return Triple(4, -value, c)
        }
        extractMediaValue(c, "max-height")?.let { value ->
            return Triple(5, -value, c)
        }

        // 6. Other interaction states (in logical order)
        when {
            c.contains("prefers-reduced-motion") -> return Triple(6, 0, c)
            c.contains("prefers-contrast") -> return Triple(6, 1, c)
            c.contains("orientation") && c.contains("portrait") -> return Triple(6, 2, c)
            c.contains("orientation") && c.contains("landscape") -> return Triple(6, 3, c)
            c.contains("hover") && c.contains("none") -> return Triple(6, 4, c)
            c.contains("hover") -> return Triple(7, 0, c)
            c.contains("focus") -> return Triple(7, 1, c)
            c.contains("active") -> return Triple(7, 2, c)
            c.contains("print") -> return Triple(8, 0, c)
            c.contains("screen") -> return Triple(8, 1, c)
        }

        // 7. Everything else
        return Triple(9, null, c)
    }

    /**
     * Extracts numeric value from media queries like "max-width: 768px"
     * Handles multiple units and converts to consistent px values for comparison
     */
    private fun extractMediaValue(context: String, property: String): Int? {
        val regex = Regex("""$property:\s*([+-]?\d*\.?\d+)(px|rem|em|vh|vw|%|ch|ex|cm|mm|in|pt|pc|vmin|vmax)?""")
        val match = regex.find(context) ?: return null

        val numberStr = match.groupValues[1]
        val unit = match.groupValues[2].ifEmpty { "px" }

        val number = numberStr.toDoubleOrNull() ?: return null

        // Convert to px equivalent for consistent comparison
        return when (unit.lowercase()) {
            "px" -> number.toInt()
            "rem", "em" -> (number * 16).toInt() // Assume 16px base
            "vh" -> (number * 7.68).toInt() // Assume 768px viewport height
            "vw" -> (number * 13.66).toInt() // Assume 1366px viewport width
            "%" -> (number * 10).toInt() // Rough conversion for sorting
            "ch" -> (number * 8).toInt() // Assume 8px character width
            "cm" -> (number * 37.8).toInt() // 1cm ≈ 37.8px
            "mm" -> (number * 3.78).toInt() // 1mm ≈ 3.78px
            "in" -> (number * 96).toInt() // 1in = 96px
            "pt" -> (number * 1.33).toInt() // 1pt ≈ 1.33px
            "pc" -> (number * 16).toInt() // 1pc = 16px
            "vmin", "vmax" -> (number * 10).toInt() // Rough estimate
            else -> number.toInt()
        }.coerceAtLeast(0) // Ensure no negative values
    }
}