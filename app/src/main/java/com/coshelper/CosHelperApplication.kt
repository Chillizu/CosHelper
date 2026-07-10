package com.coshelper

import android.app.Application
import com.coshelper.utils.AppLogger

class CosHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
