package com.doughindson.karootunes

import com.doughindson.karootunes.playback.PlayerRepository
import com.doughindson.karootunes.widget.PlayerDataType
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import org.koin.android.ext.android.inject

/** Entry point the Karoo binds to. Keeps the player in sync for as long as the extension runs. */
class KarooTunesExtension : KarooExtension(PlayerDataType.EXTENSION_ID, BuildConfig.VERSION_NAME) {
    private val player: PlayerRepository by inject()
    private val playerDataType: PlayerDataType by inject()

    override val types: List<DataTypeImpl> by lazy { listOf(playerDataType) }

    override fun onCreate() {
        super.onCreate()
        player.start()
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }
}
