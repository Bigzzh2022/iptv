package com.lean.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lean.tv.data.dao.ChannelDao
import com.lean.tv.data.dao.SourceDao
import com.lean.tv.data.entity.ChannelEntity
import com.lean.tv.data.entity.SourceEntity

@Database(
    entities = [SourceEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceDao(): SourceDao

    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
