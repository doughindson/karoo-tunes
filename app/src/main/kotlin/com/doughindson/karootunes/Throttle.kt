package com.doughindson.karootunes

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Leading-edge throttle with a guaranteed trailing emission: a value is emitted immediately if
 * [timeout] has elapsed since the last emission, otherwise it's remembered and re-emitted once
 * the window reopens (rather than silently dropped, as a naive leading-edge-only throttle would).
 * Rapid values arriving inside the window collapse into a single trailing emission of the latest
 * value, not one per dropped value.
 */
fun<T> Flow<T>.throttle(timeout: Long): Flow<T> = channelFlow {
    val mutex = Mutex()
    var lastEmissionTime = 0L
    var pendingValue: T? = null
    var trailingJob: Job? = null

    collect { value ->
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastEmissionTime
            if (elapsed >= timeout) {
                trailingJob?.cancel()
                pendingValue = null
                lastEmissionTime = now
                send(value)
            } else {
                pendingValue = value
                if (trailingJob?.isActive != true) {
                    val waitFor = timeout - elapsed
                    trailingJob = launch {
                        delay(waitFor)
                        mutex.withLock {
                            val v = pendingValue
                            pendingValue = null
                            if (v != null) {
                                lastEmissionTime = System.currentTimeMillis()
                                send(v)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun<T> Flow<T>.onlyIfNValuesReceivedWithinTimeframe(n: Int, timeframe: Long): Flow<T> = flow {
    val lastValuesReceivedAt = mutableListOf<Long>()

    collect { value ->
        val currentTime = System.currentTimeMillis()

        lastValuesReceivedAt.removeAll { it + timeframe < currentTime }
        lastValuesReceivedAt.add(currentTime)

        if (lastValuesReceivedAt.size >= n) {
            emit(value)
        }
    }
}