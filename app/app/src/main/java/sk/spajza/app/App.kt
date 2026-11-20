package sk.spajza.app

import android.app.Application
import android.util.Log
import sk.spajza.app.data.Store
import sk.spajza.app.notify.Notify

class App : Application() {

    val store: Store by lazy { Store(this) }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        try {
            Notify.createChannel(this)
        } catch (e: Exception) {
            Log.e("App", "Kanal upozorneni", e)
        }
    }
}
