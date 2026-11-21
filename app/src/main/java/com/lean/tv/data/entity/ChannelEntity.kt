package com.lean.tv.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["name"])
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val group: String,
    val logoUrl: String,
    val streamUrl: String,
    val sourceId: Long
)
