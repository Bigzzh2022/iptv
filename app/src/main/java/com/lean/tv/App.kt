package com.lean.tv

import android.app.Application
import com.lean.tv.data.AppDatabase
import com.lean.tv.data.ChannelRepository
import com.lean.tv.data.ConfigStore
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        MMKV.initialize(this)
        ConfigStore.init()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        database = AppDatabase.get(this)
        repository = ChannelRepository(database)
    }

    companion object {
        lateinit var instance: App
            private set

        lateinit var appScope: CoroutineScope
            private set

        lateinit var database: AppDatabase
            private set

        lateinit var repository: ChannelRepository
            private set
    }
}
