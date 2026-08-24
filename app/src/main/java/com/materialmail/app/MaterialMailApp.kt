package com.materialmail.app

import android.app.Application
import com.materialmail.appshell.AppContainer
import com.materialmail.appshell.AppContainerProvider
import com.materialmail.appshell.ShellBootstrap

class MaterialMailApp : Application(), AppContainerProvider {

    override lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = ShellBootstrap.init(this)
    }
}