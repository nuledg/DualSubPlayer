package com.dualsub.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SubtitleSelectionDao {

    @Query(
        "SELECT * FROM subtitle_selections " +
            "WHERE videoName = :name AND videoSize = :size LIMIT 1"
    )
    suspend fun find(name: String, size: Long): SubtitleSelectionEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SubtitleSelectionEntry)

    @Query(
        "UPDATE subtitle_selections SET updatedAt = :updatedAt " +
            "WHERE videoName = :name AND videoSize = :size"
    )
    suspend fun touch(name: String, size: Long, updatedAt: Long)

    @Query("DELETE FROM subtitle_selections WHERE updatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
