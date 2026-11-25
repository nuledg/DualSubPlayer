package com.dualsub.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val ScrimDark = Color(0xCC000000)

/** The bars swallow empty-area touches unless edge-time taps are enabled. */
private fun Modifier.blockTouches(enabled: Boolean): Modifier =
    if (enabled) pointerInput(Unit) { detectTapGestures { } }
    else this

/** A round icon button with a soft glass look. */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), CircleShape)
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/** A slim seek rail with a small thumb and a generous invisible touch target. */
@Composable
private fun RowScope.MinimalSeekSlider(
    value: Long,
    duration: Long,
    scrubbing: Boolean,
    onScrubValueChange: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = Color.White.copy(alpha = 0.28f)
    val total = duration.coerceAtLeast(1L)

    Canvas(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 12.dp)
            .height(28.dp)
            .pointerInput(total) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackWidth = size.width.toFloat().coerceAtLeast(1f)
                    val downX = down.position.x
                    var dragging = false
                    down.consume()

                    fun positionAt(x: Float): Long =
                        ((x / trackWidth).coerceIn(0f, 1f) * total.toFloat()).toLong()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change: PointerInputChange =
                            event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            val target = positionAt(change.position.x)
                            if (dragging) {
                                onScrubValueChange(target)
                                onSeek(target)
                                dragging = false
                                onScrubbingChange(false)
                            } else {
                                onSeek(target)
                            }
                            change.consume()
                            break
                        }

                        if (!dragging && abs(change.position.x - downX) > viewConfiguration.touchSlop) {
                            dragging = true
                            onScrubbingChange(true)
                        }
                        if (dragging) {
                            onScrubValueChange(positionAt(change.position.x))
                            change.consume()
                        }
                    }
                    if (dragging) onScrubbingChange(false)
                }
            }
    ) {
        val centerY = size.height / 2f
        // Slightly larger for visibility without making the minimal rail feel bulky.
        val thumbRadius = 4.5.dp.toPx()
        val railStart = thumbRadius
        val railEnd = (size.width - thumbRadius).coerceAtLeast(railStart)
        val fraction = (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val progressX = railStart + (railEnd - railStart) * fraction
        val stroke = 2.dp.toPx()

        drawLine(
            color = inactive,
            start = Offset(railStart, centerY),
            end = Offset(railEnd, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = active,
            start = Offset(railStart, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = active,
            radius = if (scrubbing) 5.5.dp.toPx() else thumbRadius,
            center = Offset(progressX, centerY)
        )
    }
}

/**
 * Shown by long press or when the middle tap pauses playback; hides after 4 seconds
 * (PlayerScreen owns that timer). The timeline and action icons are independently configurable.
 */
@Composable
fun BoxScope.ControlsOverlay(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    showTotalDuration: Boolean,
    onToggleTimeDisplay: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSwapClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showTimeline: Boolean = true,
    showActions: Boolean = true,
    allowEdgeTimeTaps: Boolean = false,
    forceAllControls: Boolean = false
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val shown = if (scrubbing) scrubValue.toLong() else position

    val timelineVisible = showTimeline || forceAllControls
    val actionsVisible = showActions || forceAllControls
    val blockEmptyAreaTouches = !allowEdgeTimeTaps

    // Top: title and action icons (independently configurable).
    if (actionsVisible) Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ScrimDark, Color.Transparent)))
            .blockTouches(blockEmptyAreaTouches)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        GlassIconButton({
            onSwapClick()
            onInteraction()
        }, Icons.Filled.SwapHoriz, "Swap subtitles")
        Row(Modifier.padding(start = 8.dp)) {
            GlassIconButton({ onSubtitlesClick(); onInteraction() }, Icons.Filled.Subtitles, "Subtitles")
        }
        Row(Modifier.padding(start = 8.dp)) {
            GlassIconButton({ onSettingsClick(); onInteraction() }, Icons.Filled.Settings, "Settings")
        }
    }

    // Bottom: elapsed time, minimal seek rail, right-side time, and play/pause.
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, ScrimDark)))
            .blockTouches(blockEmptyAreaTouches)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (timelineVisible) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTime(shown),
                color = Color.White,
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium
            )
            MinimalSeekSlider(
                value = shown,
                duration = duration,
                scrubbing = scrubbing,
                onScrubValueChange = { scrubValue = it.toFloat() },
                onSeek = onSeek,
                onScrubbingChange = { active ->
                    scrubbing = active
                    onScrubbingChange(active)
                }
            )
            val rightTime = if (showTotalDuration) {
                formatTime(duration)
            } else {
                "-${formatTime((duration - shown).coerceAtLeast(0L))}"
            }
            Text(
                text = rightTime,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(horizontal = 5.dp, vertical = 6.dp)
                    .clickable(onClick = onToggleTimeDisplay)
            )
        }
        if (timelineVisible) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = {
                    onPlayPause()
                    onInteraction()
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}
