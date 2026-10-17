package com.rakib.bushra

import android.app.Application
import com.rakib.bushra.core.BushraCore

class BushraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BushraCore.init(this)
    }
}
