package com.dualsub.player.subtitle

import kotlin.math.roundToInt

/** Where subtitle blocks go on the screen. Pure math, so it is tested without a device. */
object SubtitlePlacement {

    /** A placed block, in pixels. */
    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /** Left edge for a block of [size] whose center is at [percent] of [parentSize]. Kept inside the parent. */
    fun start(percent: Float, size: Int, parentSize: Int): Int =
        (parentSize * percent / 100f - size / 2f).roundToInt().coerceIn(0, (parentSize - size).coerceAtLeast(0))

    /**
     * Where S2 goes. Normally where the user put it. But S2 is meant to sit ABOVE S1, and when both
     * are two lines tall they can touch. Then S2 moves up so its bottom edge stays [gap] above S1.
     * Nothing moves when S2 was placed below S1 on purpose, or when the two do not share any width.
     */
    fun s2Top(s1: Box, s2: Box, gap: Int, s2Percent: Float, s1Percent: Float): Int {
        if (s2Percent >= s1Percent) return s2.top
        val sharesWidth = s2.left < s1.right && s1.left < s2.right
        if (!sharesWidth) return s2.top
        val highest = s1.top - gap - s2.height
        return minOf(s2.top, highest).coerceAtLeast(0)
    }
}
