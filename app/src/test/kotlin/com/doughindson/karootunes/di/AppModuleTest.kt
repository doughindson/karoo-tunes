package com.doughindson.karootunes.di

import android.content.Context
import com.doughindson.karootunes.ui.TrackListRoute
import org.junit.Test
import java.io.File
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class AppModuleTest {
    /**
     * Fails if any class's constructor needs something no module provides (a crash at app start).
     * The extra types are passed explicitly in the module (cache dir, client ID, clocks, nav route).
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `dependency graph is complete`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                TrackListRoute::class,
                File::class,
                String::class,
                Int::class,
                Function0::class,
                Function1::class,
                Function2::class,
            ),
        )
    }
}
