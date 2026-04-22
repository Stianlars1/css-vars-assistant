package cssvarsassistant.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import cssvarsassistant.model.CssVarDoc
import cssvarsassistant.settings.CssVarsAssistantSettings
import cssvarsassistant.util.ARROW_UP_RIGHT
import cssvarsassistant.util.ValueUtil
import kotlin.math.pow
import kotlin.math.roundToInt


// Phase 8b — one row of the hover popup's value table. `sourceFile` + `sourceLine`
// populate the Source column with `file.css:N`; both default to null when the
// index cache is a legacy 3-part record and the first resolution step is used
// as a fallback label instead.
data class HoverRow(
    val context: String,
    val resInfo: ResolutionInfo,
    val comment: String,
    val sourceFile: String?,
    val sourceLine: Int?
)

fun buildHtmlDocument(
    varName: String,
    doc: CssVarDoc,
    sorted: List<HoverRow>,
    showPixelCol: Boolean,
    winnerIndex: Int = -1  // Default for backward compatibility
): String {
    val settings = CssVarsAssistantSettings.getInstance()
    val columnVisibility = settings.columnVisibility

    /* ── dynamic column decisions ─────────────────────────────────────────── */
    val hasColorValues = sorted.any { ColorParser.parseCssColor(it.resInfo.resolved) != null }
    val hasNonHexColors = sorted.any { row ->
        ColorParser.parseCssColor(row.resInfo.resolved)?.let { !row.resInfo.resolved.trim().startsWith("#") } ?: false
    }
    val hasName = doc.name.isNotBlank()

    // Determine which columns to actually show
    val showContextCol = columnVisibility.showContext
    val showColorSwatchCol = columnVisibility.showColorSwatch && hasColorValues
    val showValueCol = columnVisibility.showValue
    val showTypeCol = columnVisibility.showType
    val showSourceCol = columnVisibility.showSource
    val showPixelEqCol = columnVisibility.showPixelEquivalent && showPixelCol
    val showHexCol = columnVisibility.showHexValue && hasColorValues && hasNonHexColors
    val showWcagCol = columnVisibility.showWcagContrast && hasColorValues

    // 1.8.2 — compact source display keeps the popup narrow enough that the
    // Source cell doesn't wrap at the IDE's hard max-width clamp. Full path
    // is always attached as a `title` tooltip regardless of compact state.
    val compactSource = settings.compactSourceColumn


    val winnerFirstSorted = if (winnerIndex >= 0) {
        val winner = sorted[winnerIndex]
        val others = sorted.filterIndexed { index, _ -> index != winnerIndex }
        listOf(winner) + others
    } else {
        sorted
    }

    /* ── inline-CSS helpers (survive IntelliJ trimming) ────────────────────── */
    val headerWrapperStyle =
        "style='color:#F2F2F2;padding:0px;font-weight:bold;border-bottom:1px solid #BABABA;'"
    val rowStyle = "style='white-space:nowrap;padding-top:5px;padding-bottom:5px;color:#BABABA;'"
    val rowResolvedStyle = "style='white-space:nowrap;color:#F2F2F2;display:inline-block;'"
    val space = "&nbsp;"

    /* ── builder start ─────────────────────────────────────────────────────── */
    val sb = StringBuilder()
        .append(DocumentationMarkup.DEFINITION_START)

    if (hasName)
        sb.append("<b>").append(StringUtil.escapeXmlEntities(doc.name)).append("</b><br/>")

    if (!hasName)
        sb.append("<b>").append(StringUtil.escapeXmlEntities(varName)).append("</b><br/>")
            .append(DocumentationMarkup.DEFINITION_END)
            .append(DocumentationMarkup.CONTENT_START)

    val hasResolvedValues = winnerFirstSorted.any { row ->
        row.resInfo.steps.isNotEmpty() && row.resInfo.original != row.resInfo.resolved
    }

    if (hasResolvedValues) {
        sb.append("<p><b>$ARROW_UP_RIGHT shows a resolved value</b> – hover it to see every step <i>(resolution chain)</i></p>")
    }

    /* ── table header ─────────────────────────────────────────────────────── */
    // 1.8.2 — nudge the popup wider so multi-column rows (Context, Value,
    // Type, Source, Hex, WCAG) don't wrap at the platform's default width.
    // IntelliJ's `DocumentationComponent` computes preferred size from the
    // laid-out content and clamps at MAX_DEFAULT ≈ 950 px; we hint at
    // 700 px with two belts-and-suspenders tricks:
    //   1. `min-width` on the outer wrapper (works in JBHtmlEditorKit when
    //      the CSS parser honours it),
    //   2. a zero-height spacer div (works even when min-width is ignored,
    //      because the spacer is a real laid-out block).
    // If both get stripped by the renderer on some future platform build,
    // the popup falls back to today's behaviour — no worse than 1.8.1.
    sb.append("<div style='min-width:700px;'>")
    sb.append("<div style='width:700px;height:0;overflow:hidden;'></div>")
    sb.append("""<table><tr $headerWrapperStyle>""")

    if (showContextCol) sb.append("<th><nobr>Context</nobr></th>")
    if (showColorSwatchCol) sb.append("<th>&nbsp;</th>")
    if (showValueCol) sb.append("<th><nobr>Value</nobr></th>")
    if (showTypeCol) sb.append("<th><nobr>Type</nobr></th>")
    if (showSourceCol) sb.append("<th><nobr>Source</nobr></th>")
    if (showPixelEqCol) sb.append("<th><nobr>px&nbsp;Eq.</nobr></th>")
    if (showHexCol) sb.append("<th><nobr>Hex</nobr></th>")
    if (showWcagCol) sb.append("<th><nobr>WCAG</nobr></th>")

    sb.append("</tr>")

    /* ── table rows ───────────────────────────────────────────────────────── */
    winnerFirstSorted.forEachIndexed { displayIndex, row ->
        ProgressManager.checkCanceled()

        val ctx = row.context
        val resInfo = row.resInfo

        val isWinner = displayIndex == 0 && winnerIndex >= 0
        val isOverridden = !isWinner && ctx == "default" && displayIndex > 0

        val rowStyleExtra = if (isWinner) "font-weight:bold;" else ""
        val rawValue = resInfo.resolved
        val colorObj = ColorParser.parseCssColor(rawValue)
        val isColour = colorObj != null
        val pixelEq = if (ValueUtil.isSizeValue(rawValue))
            "${ValueUtil.convertToPixels(rawValue).roundToInt()}px" else "—"
        val typeStr = ValueUtil.getValueType(rawValue).name
        // Phase 8b — `file.css:N` when the index knows where the declaration
        // lives, falling back to the legacy "first resolution step" label
        // (e.g. "calc(...)") when line info is unavailable.
        // 1.8.2 — `compactSource` renders `:N` in the cell but keeps the
        // full `file.css:N` in the tooltip (`sourceTooltip`) so nothing is
        // hidden, just compressed visually.
        val sourceStr = formatSourceCell(row.sourceFile, row.sourceLine, resInfo.steps.firstOrNull(), compactSource)
        val sourceTooltip = formatSourceCell(row.sourceFile, row.sourceLine, resInfo.steps.firstOrNull(), compact = false)
        val contrast = colorObj?.let {
            // WCAG contrast vs black
            val l = listOf(it.red, it.green, it.blue).map { ch ->
                val v = ch / 255.0
                if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }.let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }
            "%.2f:1".format((1.05) / (l + 0.05))
        } ?: "—"
        val hexValue = colorObj?.toHex() ?: "—"
        /* –– row –– */
        sb.append("<tr style='$rowStyleExtra'>")


        if (showContextCol) {
            sb.append("<td $rowStyle><nobr>${StringUtil.escapeXmlEntities(contextLabel(ctx, isColour))}</nobr></td>")
        }

        if (showColorSwatchCol) {
            sb.append("<td $rowStyle><nobr>${if (isColour) colorSwatchHtml(rawValue) else "&nbsp;"}</nobr></td>")
        }

        if (showValueCol) {
            // Create a readable tooltip with proper formatting
            val tooltipText = buildTooltipText(resInfo, rawValue)

            sb.append("<td $rowStyle title='${StringUtil.escapeXmlEntities(tooltipText)}'><nobr>")
                .append(StringUtil.escapeXmlEntities(rawValue).lowercase())

            // Mark overridden values
            if (isOverridden) {
                sb.append(" <span style='opacity:.6'><i>(overridden)</i></span>")
            }

            // Add resolution indicator
            if (resInfo.steps.isNotEmpty() && resInfo.original != resInfo.resolved) {
                sb.append("$space<span $rowResolvedStyle>$space$ARROW_UP_RIGHT$space</span>")
            }
            sb.append("</nobr></td>")
        }


        if (showTypeCol) {
            sb.append("<td $rowStyle><nobr>${StringUtil.escapeXmlEntities(typeStr).lowercase()}</nobr></td>")
        }

        if (showSourceCol) {
            // 1.8.2 — only attach a tooltip when the compact form would
            // actually hide information. In verbose mode (`compactSource`
            // off) the cell already shows `variables.css:220`; a tooltip
            // that repeats the same string is redundant.
            val needsTooltip = compactSource && sourceTooltip != sourceStr
            val titleAttr = if (needsTooltip) " title='${StringUtil.escapeXmlEntities(sourceTooltip)}'" else ""
            sb.append("<td $rowStyle$titleAttr><nobr>${StringUtil.escapeXmlEntities(sourceStr)}</nobr></td>")
        }

        if (showPixelEqCol) sb.append("<td $rowStyle><nobr>$pixelEq</nobr></td>")
        if (showHexCol) sb.append("<td $rowStyle><nobr>$hexValue</nobr></td>")
        if (showWcagCol) sb.append("<td $rowStyle><nobr>$contrast</nobr></td>")

        sb.append("</tr>")
    }
    sb.append("</table>")
    sb.append("</div>") // close min-width wrapper opened before the table

    // Phase 8b — CSS cascade is load-order + specificity-dependent; showing
    // multiple rows without this note has caused confusion ("so which value
    // wins at runtime?"). We can't simulate the full cascade here, but we
    // can make the ambiguity explicit.
    if (sorted.size > 1) {
        sb.append(
            "<p style='margin-top:6px;color:#8A8A8A;'><i><small>" +
                "Shown in index order — runtime applied value depends on stylesheet load order and specificity." +
                "</small></i></p>"
        )
    }

    /* ── description / examples ──────────────────────────────────────────── */
    if (doc.description.isNotBlank()) {
        sb.append("<p><b>Description:</b><br/>")
            .append(StringUtil.escapeXmlEntities(doc.description))
            .append("</p>")
    }
    if (doc.examples.isNotEmpty()) {
        sb.append("<p><b>Examples:</b></p><pre>")
        doc.examples.forEach { sb.append(StringUtil.escapeXmlEntities(it)).append('\n') }
        sb.append("</pre>")
    }

    /* ── WebAIM helper link for first colour found ───────────────────────── */
    sorted.firstNotNullOfOrNull { ColorParser.parseCssColor(it.resInfo.resolved) }?.let { c ->
        sb.append(
            """<p style='margin-top:10px'>
                       <a target="_blank"
                          href="https://webaim.org/resources/contrastchecker/?fcolor=${
                c.toHex().removePrefix("#")
            }&bcolor=000000">
                          Check contrast on WebAIM Contrast Checker
                       </a></p>"""
        )
    }

    sb.append(DocumentationMarkup.CONTENT_END)
    return sb.toString()
}


/* ── tiny util helpers ─────────────────────────────────────────────────────── */
fun java.awt.Color.toHex(): String =
    "#%02x%02x%02x".format(red, green, blue)

fun contextLabel(ctx: String, isColor: Boolean): String {
    if (ctx == "default") return if (isColor) "Light mode" else "Default"

    // Phase 8a / issue #19: if the context is (or begins with) a raw CSS
    // selector — attribute, class, id, pseudo-class — render it verbatim.
    // Media queries keep their existing pretty-print (Dark mode, ≥768px…).
    // Combined labels like "(prefers-color-scheme: dark) .hc" start with
    // "(", so they still go down the media-query pretty-print path and the
    // selector suffix is preserved as-is.
    if (looksLikeSelector(ctx)) return ctx

    if ("prefers-color-scheme" in ctx.lowercase()) {
        return when {
            "light" in ctx.lowercase() -> "Light mode"
            "dark" in ctx.lowercase() -> "Dark mode"
            else -> "Color scheme"
        }
    }

    if ("prefers-reduced-motion" in ctx.lowercase() && "reduce" in ctx.lowercase()) {
        return "Reduced motion"
    }

    if (Regex("\\bprint\\b", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) {
        return "Print"
    }

    if (Regex("only\\s+screen", RegexOption.IGNORE_CASE).containsMatchIn(ctx)) {
        return "Only screen"
    }

    val maxWidthRegex = Regex("""max-width:\s*(\d+)(?:px)?\s*\)""")
    val minWidthRegex = Regex("""min-width:\s*(\d+)(?:px)?\s*\)""")

    val maxWidthMatch = maxWidthRegex.find(ctx)
    val minWidthMatch = minWidthRegex.find(ctx)

    return when {
        maxWidthMatch != null && minWidthMatch != null -> {
            val maxWidth = maxWidthMatch.groupValues[1]
            val minWidth = minWidthMatch.groupValues[1]
            "${minWidth}px-${maxWidth}px"
        }

        maxWidthMatch != null -> "≤${maxWidthMatch.groupValues[1]}px"
        minWidthMatch != null -> "≥${minWidthMatch.groupValues[1]}px"
        else -> {
            ctx.replace(Regex("""@media\s+"""), "")
                .replace("screen and ", "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .takeIf { it.isNotEmpty() } ?: "Media query"
        }
    }
}

// Phase 8b — Source-column formatter. Prefers `file.css:line` when the index
// has real location metadata, falls back to the legacy "first resolution
// step" label for legacy cache records (pre-1.8.1) or synthesized entries
// that never touched a source file.
//
// 1.8.2 — `compact = true` renders only `:220` so the Source cell doesn't
// wrap at IntelliJ's max-width clamp; the caller pairs this with a `title`
// tooltip carrying the full `file.css:220` string so no info is lost.
// The fallback (first resolution step) and the em-dash sentinel are
// identical across modes — only the full file-path form is compressed.
internal fun formatSourceCell(
    file: String?,
    line: Int?,
    firstStep: String?,
    compact: Boolean
): String = when {
    file != null && line != null && line > 0 -> if (compact) ":$line" else "$file:$line"
    !firstStep.isNullOrBlank() -> firstStep
    else -> "—"
}

// Heuristic: context starts with a character that marks a CSS selector rather
// than a media-query chunk. `.dark`, `#id`, `[data-*]`, `:hover`, `:not(.x)`,
// or a bare element selector like `main` all qualify. Media queries always
// begin with `(` once `@media` has been stripped by the parser.
private fun looksLikeSelector(ctx: String): Boolean {
    val first = ctx.firstOrNull() ?: return false
    return first == '.' || first == '#' || first == '[' || first == ':' ||
        first == '&' || first.isLetter()
}

fun colorSwatchHtml(css: String): String =
    ColorParser.parseCssColor(css)?.let {
        "<div style='background-color:${it.toHex()};border:1px solid #FFFFFF;display:inline-block;width:14px;height:14px;'></div>"
    } ?: "&nbsp;"

/**
 * Creates a readable tooltip showing the complete resolution chain
 */
private fun buildTooltipText(resInfo: ResolutionInfo, finalValue: String): String {
    val steps = resInfo.steps
    if (steps.isEmpty()) return "Resolved through variable references"

    val hasAt = steps.any { '@' in it }
    val hasDollar = steps.any { '$' in it }
    val hasCalc = steps.any { '=' in it }
    val legends = buildList {
        if (hasAt) add("@ = LESS/SCSS")
        if (hasDollar) add("$ = SCSS")
        if (hasCalc) add("= = calculation")
        if (steps.any { it.startsWith("calc(") }) add("calc() = CSS calc() function")
        if (steps.any { it.startsWith("var(") }) add("var() = CSS variable reference")
        if (steps.any { it.startsWith("clamp(") }) add("clamp() = CSS clamp() function")
        if (steps.any { it.startsWith("min(") || it.startsWith("max(") }) add("min()/max() = CSS min/max functions")
        if (steps.any { it.startsWith("rgb(") || it.startsWith("rgba(") }) add("rgb()/rgba() = CSS color functions")
        if (steps.any { it.startsWith("hsl(") || it.startsWith("hsla(") }) add("hsl()/hsla() = CSS color functions")
        if (steps.any { it.startsWith("url(") }) add("url() = CSS URL function")
        if (steps.any { it.startsWith("linear-gradient(") }) add("linear-gradient() = CSS gradient function")
        if (steps.any { it.startsWith("radial-gradient(") }) add("radial-gradient() = CSS gradient function")
        if (steps.any { it.startsWith("conic-gradient(") }) add("conic-gradient() = CSS gradient function")
        if (steps.any { it.startsWith("--") }) add("-- = CSS custom property")
        if (steps.any { it.startsWith("var(--") }) add("var(-- = CSS custom property reference")
        if (steps.any { it.startsWith("calc(var(--") }) add("calc(var(-- = CSS custom property calculation")
        if (steps.any { it.startsWith("clamp(var(--") }) add("clamp(var(-- = CSS custom property clamp")
        if (steps.any { it.startsWith("min(var(--") || it.startsWith("max(var(--") }) add("min(var(--/max(var(-- = CSS custom property min/max")
        if (steps.any { it.startsWith("rgb(var(--") || it.startsWith("rgba(var(--") }) add("rgb(var(--/rgba(var(-- = CSS custom property color functions")
        if (steps.any { it.startsWith("hsl(var(--") || it.startsWith("hsla(var(--") }) add("hsl(var(--/hsla(var(-- = CSS custom property color functions")
        if (steps.any { it.startsWith("url(var(--") }) add("url(var(-- = CSS custom property URL function")
    }

    return buildString {
        append("<html><div style='font-family:monospace;color:#F2F2F2;'>")
        append("<b>Resolution chain:</b><br/><br/>")

        steps.forEachIndexed { i, s ->
            val colour = when {
                '@' in s -> "#FFB347"
                '$' in s -> "#DDA0DD"
                '=' in s -> "#90EE90"
                s.startsWith("calc(") -> "#ADD8E6"
                s.startsWith("var(") -> "#FF6347"
                s.startsWith("clamp(") -> "#FFD700"
                s.startsWith("min(") || s.startsWith("max(") -> "#FF69B4"
                s.startsWith("rgb(") || s.startsWith("rgba(") -> "#FF4500"
                s.startsWith("hsl(") || s.startsWith("hsla(") -> "#FF8C00"
                s.startsWith("url(") -> "#00CED1"
                s.startsWith("linear-gradient(") -> "#20B2AA"
                s.startsWith("radial-gradient(") -> "#3CB371"
                s.startsWith("conic-gradient(") -> "#2E8B57"
                // css `--`
                s.startsWith("--") -> "#FF1493"


                else -> "#F2F2F2"
            }
            append("${i + 1}. <span style='color:$colour;'>$s</span>")
            val INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
            val ARROW = "⬇"

            if (i < steps.lastIndex) {
                append(
                    "<div style='text-align:left;margin-top:3px;margin-bottom:3px;'>" +
                            INDENT +
                            "<span style='font-weight:bold;font-size:10px;'>" +
                            ARROW +
                            "</span>" +
                            "</div>"
                )
            }
        }


        append("<br/><br/><b style='color:#98FB98;'>Final value: $finalValue</b>")
        if (legends.isNotEmpty()) append("<br/><br/><small>(${legends.joinToString(", ")})</small>")
        append("</div></html>")
    }
}

