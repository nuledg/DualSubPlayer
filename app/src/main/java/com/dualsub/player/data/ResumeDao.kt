package com.dualsub.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResumeDao {

    @Query(
        "SELECT * FROM resume_entries " +
            "WHERE fileName = :name AND fileSize = :size AND durationMs = :duration LIMIT 1"
    )
    suspend fun find(name: String, size: Long, duration: Long): ResumeEntry?

    // Same key replaces the old row, so this is also "update".
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ResumeEntry)

    @Query(
        "DELETE FROM resume_entries " +
            "WHERE fileName = :name AND fileSize = :size AND durationMs = :duration"
    )
    suspend fun delete(name: String, size: Long, duration: Long)

    @Query("DELETE FROM resume_entries WHERE lastOpenedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
