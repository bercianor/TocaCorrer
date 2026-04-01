package es.bercianor.tocacorrer

import android.app.Application
import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.local.PreferencesManager
import es.bercianor.tocacorrer.util.Strings

class TocaCorrerApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Seed Strings with the persisted language setting (no Context stored — just the Int)
        Strings.setLanguageSetting(PreferencesManager(this).language)
    }

    companion object {
        lateinit var instance: TocaCorrerApp
            private set
    }
}
