package app.lteguard

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        lateinit var ctx: Context
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        AppLog.init(this)
        ShizukuBridge.init(this)
    }
}
