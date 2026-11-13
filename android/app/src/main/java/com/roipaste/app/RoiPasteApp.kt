package com.roipaste.app

import android.app.Application
import com.roipaste.app.di.AppContainer

class RoiPasteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
