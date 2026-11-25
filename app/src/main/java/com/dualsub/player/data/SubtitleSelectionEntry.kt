package com.dualsub.player.data

import androidx.room.Entity

/**
 * Subtitle choices for one video, independent of its resume timestamp.
 * A null descriptor means that slot was deliberately left empty.
 */
@Entity(
    tableName = "subtitle_selections",
    primaryKeys = ["videoName", "videoSize"]
)
data class SubtitleSelectionEntry(
    val videoName: String,
    val videoSize: Long,
    val s1Descriptor: String?,
    val s2Descriptor: String?,
    val updatedAt: Long
)
