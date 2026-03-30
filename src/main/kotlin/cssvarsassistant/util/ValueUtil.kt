package cssvarsassistant.util

import cssvarsassistant.documentation.ColorParser

import java.awt.Color

object ValueUtil {
    enum class ValueType { SIZE, COLOR, NUMBER, OTHER }
    private val sizeValueRegex = Regex(
        """^\d+(\.\d+)?(px|rem|em|%|vh|vw|vmin|vmax|svh|svw|lvh|lvw|dvh|dvw|pt|pc|ch|ex|cm|mm|in)$""",
        RegexOption.IGNORE_CASE
    )

    
    fun getValueType(value: String): ValueType {
        val cleaned = value.trim()
        return when {
            isSizeValue(cleaned) -> {
                ValueType.SIZE
            }

            ColorParser.parseCssColor(cleaned) != null -> {
                ValueType.COLOR
            }

            isNumericValue(cleaned) -> {
                ValueType.NUMBER
            }

            else -> {
                ValueType.OTHER
            }
        }
    }


    fun isSizeValue(value: String): Boolean {
        val cleaned = value.trim()
        return sizeValueRegex.matches(cleaned)
    }

    fun isNumericValue(value: String): Boolean {
        val isNumericVal = value.trim().toDoubleOrNull() != null
        return isNumericVal
    }

    fun convertToPixels(value: String): Double {
        val trimmed = value.trim()
        val match = Regex("""^(\d+(?:\.\d+)?)([a-z%]+)$""", RegexOption.IGNORE_CASE).find(trimmed)
        val number = match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val unit = match?.groupValues?.get(2)?.lowercase().orEmpty()


        return when {
            unit == "rem" -> number * 16
            unit == "em" -> number * 16
            unit == "px" -> number
            unit == "pt" -> number * 1.33
            unit == "pc" -> number * 16
            unit == "%" -> number
            unit == "vh" || unit == "svh" || unit == "lvh" || unit == "dvh" -> number * 10
            unit == "vw" || unit == "svw" || unit == "lvw" || unit == "dvw" -> number * 10
            unit == "vmin" || unit == "vmax" -> number * 10
            unit == "ch" -> number * 8
            unit == "ex" -> number * 7.5
            unit == "cm" -> number * 37.8
            unit == "mm" -> number * 3.78
            unit == "in" -> number * 96
            else -> number
        }
    }

    fun compareSizes(a: String, b: String): Int {
        val aPixels = convertToPixels(a)
        val bPixels = convertToPixels(b)
        return aPixels.compareTo(bPixels)
    }

    fun compareNumbers(a: String, b: String): Int {
        val aNum = a.trim().toDoubleOrNull() ?: 0.0
        val bNum = b.trim().toDoubleOrNull() ?: 0.0
        return aNum.compareTo(bNum)
    }

    fun compareColors(a: String, b: String): Int {
        val colorA = ColorParser.parseCssColor(a)
        val colorB = ColorParser.parseCssColor(b)

        if (colorA == null || colorB == null) {
            return a.compareTo(b, true)
        }

        // Sort by HSB: Hue first, then Saturation, then Brightness
        val hsbA = Color.RGBtoHSB(colorA.red, colorA.green, colorA.blue, null)
        val hsbB = Color.RGBtoHSB(colorB.red, colorB.green, colorB.blue, null)

        // Compare hue (0-1)
        val hueCompare = hsbA[0].compareTo(hsbB[0])
        if (hueCompare != 0) return hueCompare

        // Compare saturation
        val satCompare = hsbA[1].compareTo(hsbB[1])
        if (satCompare != 0) return satCompare

        // Compare brightness
        return hsbA[2].compareTo(hsbB[2])
    }
}
