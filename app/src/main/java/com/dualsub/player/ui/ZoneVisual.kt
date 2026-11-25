package com.dualsub.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualsub.player.player.TapZone
import com.dualsub.player.player.ZoneLayout

fun zoneColor(zone: TapZone): Color = when (zone) {
    TapZone.REWIND -> Color(0xFFFF7A8A)
    TapZone.PLAY_PAUSE -> Color(0xFF6EE7B7)
    TapZone.FORWARD -> Color(0xFF7DB5FF)
}

fun zoneIcon(zone: TapZone): ImageVector = when (zone) {
    TapZone.REWIND -> Icons.Filled.FastRewind
    TapZone.PLAY_PAUSE -> Icons.Filled.PlayArrow
    TapZone.FORWARD -> Icons.Filled.FastForward
}

fun zoneLabel(zone: TapZone): String = when (zone) {
    TapZone.REWIND -> "Rewind"
    TapZone.PLAY_PAUSE -> "Play / pause"
    TapZone.FORWARD -> "Forward"
}

/**
 * The three tap zones as a picture. The settings preview and the screen shown after a pause use
 * this same drawing, so what you see in Settings is what you get in the player.
 * Give it a size with [modifier].
 */
@Composable
fun ZoneVisual(
    layout: ZoneLayout,
    showAxes: Boolean,
    showIcons: Boolean,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
    tint: Float = 0.22f
) {
    Row(modifier) {
        layout.order.forEachIndexed { index, zone ->
            Box(
                modifier = Modifier
                    .weight(layout.widths[index].toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .background(zoneColor(zone).copy(alpha = tint)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (showIcons) {
                        Icon(
                            imageVector = zoneIcon(zone),
                            contentDescription = zoneLabel(zone),
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(if (showLabels) 22.dp else 48.dp)
                        )
                    }
                    if (showLabels) {
                        Text(zoneLabel(zone), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                        Text("${layout.widths[index]}%", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
            }
            if (showAxes && index < layout.order.size - 1) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.5f)))
            }
        }
    }
}
