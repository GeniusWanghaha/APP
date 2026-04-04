package com.photosentinel.health

import android.app.Application
import com.photosentinel.health.infrastructure.di.AppContainer

class PhotoSentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(this)
    }
}
