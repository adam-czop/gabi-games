package com.familygames

import android.app.Application
import com.familygames.data.AppInitializer
import com.familygames.di.appModules
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class FamilyGamesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Logging only in debug: release builds must not log photo or recording paths (CLAUDE.md).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.NONE)
            androidContext(this@FamilyGamesApp)
            modules(appModules)
        }
        get<AppInitializer>().start()
    }
}
