package com.dualsub.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** What the touch layer reports. PlayerScreen decides what each event does. */
interface PlayerGestureListener {
    fun onTap(x: Float, width: Float, y: Float, height: Float)
    fun onLongPress()

    /** [leftHalf] is true when the swipe started on the left 50% of the screen. */
    fun onVerticalStart(leftHalf: Boolean)
    /** [totalDy] is the distance since the axis lock. Negative means finger moved up. */
    fun onVerticalDrag(leftHalf: Boolean, totalDy: Float, height: Float)
    fun onVerticalEnd()

    fun onHorizontalStart()
    /** [totalDx] is the distance since the axis lock. */
    fun onHorizontalDrag(totalDx: Float, width: Float)
    /** [cancelled] is true when the system took the touch away. Do not commit then. */
    fun onHorizontalEnd(cancelled: Boolean)
}

fun Modifier.playerGestures(listener: PlayerGestureListener): Modifier =
    pointerInput(listener) { detectPlayerGestures(listener) }

private sealed interface Start {
    data object Tap : Start
    data object Cancel : Start
    data class Move(val position: Offset) : Start
}

/**
 * Priority: long press > swipe > tap.
 *
 * 1. Finger goes down. We wait until one of three things happens:
 *    - it lifts before the long-press timeout and never left the slop circle -> TAP
 *    - it leaves the slop circle -> SWIPE (axis is locked right now)
 *    - the timeout passes -> LONG PRESS
 * 2. Because a tap is only reported when the finger never left the slop circle,
 *    a swipe can never trigger a tap.
 */
suspend fun PointerInputScope.detectPlayerGestures(listener: PlayerGestureListener) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val width = size.width.toFloat()
        val height = size.height.toFloat()

        val start: Start? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: Start? = null
            while (result == null) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                result = when {
                    change == null -> Start.Cancel
                    !change.pressed -> Start.Tap
                    (change.position - down.position).getDistance() > slop ->
                        Start.Move(change.position)
                    else -> null
                }
            }
            result
        }

        when (start) {
            Start.Cancel -> Unit

            Start.Tap -> listener.onTap(down.position.x, width, down.position.y, height)

            // The timeout passed with no movement and no lift: long press.
            null -> {
                listener.onLongPress()
                // Wait for the finger to lift so this touch can never become a tap or swipe.
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    change.consume()
                }
            }

            is Start.Move -> {
                val origin = start.position
                val dx = origin.x - down.position.x
                val dy = origin.y - down.position.y

                if (abs(dx) > abs(dy)) {
                    // Axis lock: horizontal
                    listener.onHorizontalStart()
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            cancelled = true
                            break
                        }
                        if (!change.pressed) break
                        change.consume()
                        listener.onHorizontalDrag(change.position.x - origin.x, width)
                    }
                    listener.onHorizontalEnd(cancelled)
                } else {
                    // Axis lock: vertical. Left half = volume, right half = brightness.
                    val leftHalf = down.position.x < width / 2f
                    listener.onVerticalStart(leftHalf)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                        listener.onVerticalDrag(leftHalf, change.position.y - origin.y, height)
                    }
                    listener.onVerticalEnd()
                }
            }
        }
    }
}
