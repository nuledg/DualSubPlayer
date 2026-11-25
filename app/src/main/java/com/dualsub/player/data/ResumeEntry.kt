package com.dualsub.player.data

import androidx.room.Entity

/**
 * One saved playback position.
 * The key is file name + file size + duration, so it survives URI changes.
 */
@Entity(
    tableName = "resume_entries",
    primaryKeys = ["fileName", "fileSize", "durationMs"]
)
data class ResumeEntry(
    val fileName: String,
    val fileSize: Long,
    val durationMs: Long,
    val positionMs: Long,
    val lastOpenedAt: Long
)
