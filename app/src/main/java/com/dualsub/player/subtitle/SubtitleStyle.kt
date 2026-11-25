package com.dualsub.player.subtitle

/**
 * How one subtitle (S1 or S2) looks and where it sits. No Android classes, so it can be tested anywhere.
 * Colors are ARGB integers. Positions are percentages of the screen: the center of the subtitle
 * block is at [xPercent] of the width and [yPercent] of the height.
 */
data class SubtitleStyle(
    /** "default", "serif", "mono", "cursive" or "custom" (a font file the user added). */
    val font: String,
    val customFontPath: String?,
    val sizeSp: Float,
    val colorArgb: Int,
    val bold: Boolean,
    /** 0 means no outline. The outline is always black. */
    val outlineWidth: Float,
    val shadow: Boolean,
    val backgroundRgb: Int,
    /** 0 = no background, 1 = solid. */
    val backgroundOpacity: Float,
    val xPercent: Float,
    val yPercent: Float
) {

    /** Keeps every value inside a sensible range. */
    fun coerced(): SubtitleStyle = copy(
        sizeSp = sizeSp.coerceIn(MIN_SIZE, MAX_SIZE),
        outlineWidth = outlineWidth.coerceIn(0f, MAX_OUTLINE),
        backgroundOpacity = backgroundOpacity.coerceIn(0f, 1f),
        xPercent = xPercent.coerceIn(0f, 100f),
        yPercent = yPercent.coerceIn(0f, 100f)
    )

    /** One line of text such as "font=default;size=22.0;...". Stored in the settings. */
    fun serialize(): String = listOf(
        "font=$font",
        "fontPath=${customFontPath.orEmpty()}",
        "size=$sizeSp",
        "color=$colorArgb",
        "bold=${if (bold) 1 else 0}",
        "outline=$outlineWidth",
        "shadow=${if (shadow) 1 else 0}",
        "bg=$backgroundRgb",
        "bgOpacity=$backgroundOpacity",
        "x=$xPercent",
        "y=$yPercent"
    ).joinToString(";")

    companion object {
        const val FONT_DEFAULT = "default"
        const val FONT_SERIF = "serif"
        const val FONT_MONO = "mono"
        const val FONT_CURSIVE = "cursive"
        const val FONT_CUSTOM = "custom"

        const val MIN_SIZE = 12f
        const val MAX_SIZE = 48f
        const val MAX_OUTLINE = 6f

        val WHITE = 0xFFFFFFFF.toInt()
        val YELLOW = 0xFFFFEB3B.toInt()

        /** Text colors offered in the settings. */
        val TEXT_COLORS = listOf(
            WHITE, YELLOW, 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF80AB.toInt(),
            0xFF80DEEA.toInt(), 0xFF69F0AE.toInt(), 0xFFB0BEC5.toInt(), 0xFF000000.toInt()
        )

        /** Background colors offered in the settings (RGB, the opacity is separate). */
        val BACKGROUND_COLORS = listOf(0x000000, 0x263238, 0x0D1B3E, 0x3E2723, 0xFFFFFF)

        /** S1 is white near the bottom. S2 is yellow above it. */
        fun defaultFor(slot: SubtitleSlot): SubtitleStyle = SubtitleStyle(
            font = FONT_DEFAULT,
            customFontPath = null,
            sizeSp = 22f,
            colorArgb = if (slot == SubtitleSlot.S1) WHITE else YELLOW,
            bold = false,
            outlineWidth = 1.5f,
            shadow = true,
            backgroundRgb = 0x000000,
            backgroundOpacity = 0.5f,
            xPercent = 50f,
            yPercent = if (slot == SubtitleSlot.S1) 90f else 79f
        )

        /** Reads text made by [serialize]. Missing or broken values fall back to [fallback]. */
        fun parse(text: String?, fallback: SubtitleStyle): SubtitleStyle {
            if (text.isNullOrBlank()) return fallback
            val map = text.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
            }.toMap()
            return SubtitleStyle(
                font = map["font"]?.takeIf { it.isNotBlank() } ?: fallback.font,
                customFontPath = map["fontPath"]?.takeIf { it.isNotBlank() },
                sizeSp = map["size"]?.toFloatOrNull() ?: fallback.sizeSp,
                colorArgb = map["color"]?.toIntOrNull() ?: fallback.colorArgb,
                bold = map["bold"]?.let { it == "1" } ?: fallback.bold,
                outlineWidth = map["outline"]?.toFloatOrNull() ?: fallback.outlineWidth,
                shadow = map["shadow"]?.let { it == "1" } ?: fallback.shadow,
                backgroundRgb = map["bg"]?.toIntOrNull() ?: fallback.backgroundRgb,
                backgroundOpacity = map["bgOpacity"]?.toFloatOrNull() ?: fallback.backgroundOpacity,
                xPercent = map["x"]?.toFloatOrNull() ?: fallback.xPercent,
                yPercent = map["y"]?.toFloatOrNull() ?: fallback.yPercent
            ).coerced()
        }
    }
}
