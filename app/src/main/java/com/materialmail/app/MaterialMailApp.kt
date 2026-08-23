package com.materialmail.app

import android.app.Application
import com.materialmail.core.sync.work.SyncEngineLocator
import com.materialmail.core.sync.work.SyncScheduler

class MaterialMailApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncEngineLocator.instance = container.syncEngine
        SyncScheduler.schedulePeriodic(this)
    }
}