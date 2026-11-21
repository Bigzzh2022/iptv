package com.lean.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lean.tv.data.entity.SourceEntity

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY id ASC")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sources SET lastUpdate = :time WHERE id = :id")
    suspend fun markUpdated(id: Long, time: Long)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int
}
