package com.mati.shortformblocker

import android.app.Application
import android.content.Context
import com.mati.shortformblocker.data.SettingsRepository
import com.mati.shortformblocker.data.StatsRepository

/** Service locator. Two repositories do not justify a DI framework here. */
class BlockerApp : Application() {

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val stats: StatsRepository by lazy { StatsRepository(this) }

    companion object {
        fun from(context: Context): BlockerApp = context.applicationContext as BlockerApp
    }
}
