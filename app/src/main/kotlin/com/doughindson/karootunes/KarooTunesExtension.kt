package com.doughindson.karootunes

import com.doughindson.karootunes.datatypes.PlayerDataType
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class KarooTunesExtension : KarooExtension("karoo-tunes", BuildConfig.VERSION_NAME) {
    companion object {
        const val TAG = "karoo-tunes"
    }

    private val servicesProvider: KarooTunesServices = KarooTunesServices(
        get(), get(), get(), get(), get(), get(), get(), get(), get())
    private val playerDataType: PlayerDataType by inject()

    override val types: List<DataTypeImpl> = listOf(playerDataType)

    override fun onCreate() {
        servicesProvider.startJobs()
        super.onCreate()
    }

    override fun onDestroy() {
        servicesProvider.cancelJobs()
        super.onDestroy()
    }
}