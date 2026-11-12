package com.doughindson.karootunes.data.karoo

import android.content.Context
import android.util.Log
import com.doughindson.karootunes.R
import com.doughindson.karootunes.TAG
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.InRideAlert
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** The app's single connection to the Karoo system service. */
class KarooGateway(context: Context) {
    val service = KarooSystemService(context)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    init {
        service.connect { connected ->
            Log.d(TAG, "Karoo system connected: $connected")
            _connected.value = connected
        }
    }

    val hardwareType: HardwareType? get() = service.hardwareType

    /** The ride page currently on screen, or null before the first update. */
    fun activeRidePage(): Flow<ActiveRidePage?> = callbackFlow<ActiveRidePage?> {
        val id = service.addConsumer<ActiveRidePage> { trySend(it) }
        awaitClose { service.removeConsumer(id) }
    }.onStart { emit(null) }

    /** The selected ride profile, or null before the first update. */
    fun activeRideProfile(): Flow<ActiveRideProfile?> = callbackFlow<ActiveRideProfile?> {
        val id = service.addConsumer<ActiveRideProfile> { trySend(it) }
        awaitClose { service.removeConsumer(id) }
    }.onStart { emit(null) }

    fun isDataTypeOnScreen(dataTypeId: String): Flow<Boolean> = activeRidePage()
        .map { page -> page?.page?.elements?.any { it.dataTypeId == dataTypeId } == true }
        .distinctUntilChanged()

    fun isDataTypeInActiveProfile(dataTypeId: String): Flow<Boolean> = activeRideProfile()
        .map { profile ->
            profile?.profile?.pages?.any { page -> page.elements.any { it.dataTypeId == dataTypeId } } == true
        }
        .distinctUntilChanged()

    /** Shows a short in-ride alert. Also logs, so errors are visible when not running on a Karoo. */
    fun showAlert(title: String, message: String) {
        Log.w(TAG, "Alert: $title - $message")
        service.dispatch(
            InRideAlert(
                id = "karoo-tunes-${System.currentTimeMillis()}",
                icon = R.drawable.ic_music_note,
                title = title,
                detail = message,
                autoDismissMs = 10_000L,
                backgroundColor = R.color.alertBackground,
                textColor = R.color.alertText,
            )
        )
    }
}
