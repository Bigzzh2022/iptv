package com.lean.tv.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lean.tv.data.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY id ASC")
    suspend fun all(): List<ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY id ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId ORDER BY id ASC")
    suspend fun bySource(sourceId: Long): List<ChannelEntity>

    /**
     * 同一频道名下的其它线路，用于播放失败时自动换线。
     */
    @Query(
        "SELECT streamUrl FROM channels WHERE name = :name AND streamUrl != :exclude " +
            "ORDER BY id ASC LIMIT 5"
    )
    suspend fun alternativeUrls(name: String, exclude: String): List<String>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM channels")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countBySource(sourceId: Long): Int

    /**
     * 配置页频道预览：按名称或分组过滤，只取前 limit 条，避免大列表撑爆手机页面。
     */
    @Query(
        "SELECT * FROM channels WHERE :q = '' OR name LIKE '%' || :q || '%' " +
            "OR `group` LIKE '%' || :q || '%' ORDER BY id ASC LIMIT :limit"
    )
    suspend fun preview(q: String, limit: Int): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    @Query("DELETE FROM channels")
    suspend fun clear()
}
