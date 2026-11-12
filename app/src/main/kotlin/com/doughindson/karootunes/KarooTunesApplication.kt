package com.doughindson.karootunes

import android.app.Application
import com.doughindson.karootunes.data.auth.TokenStore
import com.doughindson.karootunes.data.auth.migrateLegacyToken
import com.doughindson.karootunes.data.settings.SettingsRepository
import com.doughindson.karootunes.di.appModules
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KarooTunesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@KarooTunesApplication)
            modules(appModules)
        }
        get<AppScope>().launch { migrateLegacyToken(get<SettingsRepository>(), get<TokenStore>()) }
    }
}
