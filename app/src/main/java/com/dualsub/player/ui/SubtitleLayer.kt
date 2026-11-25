package com.dualsub.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualsub.player.subtitle.SubtitlePlacement
import com.dualsub.player.subtitle.SubtitleStyle
import com.dualsub.player.subtitle.SubtitleWordTokenizer
import java.io.File
import kotlin.math.roundToInt

private const val WORD_ANNOTATION = "subtitle_word"
private const val ID_S1 = "s1"
private const val ID_S2 = "s2"
private const val MAX_WIDTH_FRACTION = 0.92f

/**
 * S1 and S2 are drawn separately, each at its own place and with its own style.
 * S1 is drawn last, so it stays on top. If a two-line S2 would touch S1, S2 moves up a little
 * (see [SubtitlePlacement.s2Top]).
 */
@Composable
fun SubtitleLayer(
    s1: String,
    s2: String,
    s1Style: SubtitleStyle,
    s2Style: SubtitleStyle,
    onS1WordClick: ((String) -> Unit)? = null,
    onS2WordClick: ((String) -> Unit)? = null
) {
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    Layout(
        content = {
            if (s2.isNotBlank()) {
                SubtitleText(
                    s2, s2Style, Modifier.layoutId(ID_S2),
                    onWordClick = onS2WordClick
                )
            }
            if (s1.isNotBlank()) {
                SubtitleText(
                    s1, s1Style, Modifier.layoutId(ID_S1),
                    onWordClick = onS1WordClick
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val limit = Constraints(maxWidth = (maxW * MAX_WIDTH_FRACTION).roundToInt())
        val p1 = measurables.firstOrNull { it.layoutId == ID_S1 }?.measure(limit)
        val p2 = measurables.firstOrNull { it.layoutId == ID_S2 }?.measure(limit)

        layout(maxW, maxH) {
            var box1: SubtitlePlacement.Box? = null
            if (p1 != null) {
                val x = SubtitlePlacement.start(s1Style.xPercent, p1.width, maxW)
                val y = SubtitlePlacement.start(s1Style.yPercent, p1.height, maxH)
                box1 = SubtitlePlacement.Box(x, y, p1.width, p1.height)
            }
            if (p2 != null) {
                val x = SubtitlePlacement.start(s2Style.xPercent, p2.width, maxW)
                var y = SubtitlePlacement.start(s2Style.yPercent, p2.height, maxH)
                if (box1 != null) {
                    y = SubtitlePlacement.s2Top(
                        s1 = box1,
                        s2 = SubtitlePlacement.Box(x, y, p2.width, p2.height),
                        gap = gap,
                        s2Percent = s2Style.yPercent,
                        s1Percent = s1Style.yPercent
                    )
                }
                p2.place(x, y)
            }
            if (p1 != null && box1 != null) p1.place(box1.left, box1.top)
        }
    }
}

/**
 * Puts the content so its center is at [xPercent] / [yPercent] of the parent size,
 * and keeps it inside the parent. Use it inside a Box that fills the screen.
 * (The settings preview uses this; the player screen uses [SubtitleLayer].)
 */
fun Modifier.placeAtPercent(xPercent: Float, yPercent: Float, maxWidthFraction: Float = MAX_WIDTH_FRACTION): Modifier =
    layout { measurable, constraints ->
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val placeable = measurable.measure(Constraints(maxWidth = (maxW * maxWidthFraction).roundToInt()))
        layout(maxW, maxH) {
            placeable.place(
                SubtitlePlacement.start(xPercent, placeable.width, maxW),
                SubtitlePlacement.start(yPercent, placeable.height, maxH)
            )
        }
    }

/**
 * One subtitle with its style: font, size, color, bold, outline, shadow, background.
 * [scale] shrinks everything together. The settings preview uses it to match a small screen.
 */
@Composable
fun SubtitleText(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onWordClick: ((String) -> Unit)? = null
) {
    val family = rememberSubtitleFont(style)
    val base = TextStyle(
        fontFamily = family,
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = (style.sizeSp * scale).sp,
        textAlign = TextAlign.Center,
        // The direction follows the text itself, so Persian lines are right-to-left.
        textDirection = TextDirection.Content
    )
    val outlinePx = with(LocalDensity.current) { (style.outlineWidth * scale).dp.toPx() }
    val background = Color(style.backgroundRgb or 0xFF000000.toInt()).copy(alpha = style.backgroundOpacity)

    val annotatedText = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            SubtitleWordTokenizer.words(text).forEach { word ->
                append(text.substring(cursor, word.start))
                pushStringAnnotation(tag = WORD_ANNOTATION, annotation = word.text)
                append(text.substring(word.start, word.endExclusive))
                pop()
                cursor = word.endExclusive
            }
            append(text.substring(cursor))
        }
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape((4 * scale).dp))
            .padding(horizontal = (8 * scale).dp, vertical = (2 * scale).dp)
    ) {
        if (outlinePx > 0f) {
            // The outline is the same text drawn as a thick black stroke, behind the normal text.
            Text(
                text = annotatedText,
                style = base.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = outlinePx * 2f, join = StrokeJoin.Round)
                )
            )
        }
        val foregroundStyle = base.copy(
            color = Color(style.colorArgb),
            shadow = if (style.shadow) {
                Shadow(Color.Black, Offset(2f * scale, 2f * scale), blurRadius = 4f * scale)
            } else {
                null
            }
        )
        if (onWordClick != null) {
            ClickableText(
                text = annotatedText,
                style = foregroundStyle,
                onClick = { offset ->
                    annotatedText.getStringAnnotations(WORD_ANNOTATION, offset, offset)
                        .firstOrNull()?.item?.let(onWordClick)
                }
            )
        } else {
            Text(text = annotatedText, style = foregroundStyle)
        }
    }
}

@Composable
private fun rememberSubtitleFont(style: SubtitleStyle): FontFamily =
    remember(style.font, style.customFontPath) {
        when (style.font) {
            SubtitleStyle.FONT_SERIF -> FontFamily.Serif
            SubtitleStyle.FONT_MONO -> FontFamily.Monospace
            SubtitleStyle.FONT_CURSIVE -> FontFamily.Cursive
            SubtitleStyle.FONT_CUSTOM ->
                style.customFontPath
                    ?.takeIf { File(it).exists() }
                    ?.let { path -> runCatching { FontFamily(Font(File(path))) }.getOrNull() }
                    ?: FontFamily.Default
            else -> FontFamily.Default
        }
    }
