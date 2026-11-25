package com.dualsub.player.ui

/** 3725000 -> "1:02:05", 65000 -> "1:05" */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
