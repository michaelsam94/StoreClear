package com.michael.storeclear

import android.app.Application
import com.michael.storeclear.di.AppContainer

class StoreClearApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
